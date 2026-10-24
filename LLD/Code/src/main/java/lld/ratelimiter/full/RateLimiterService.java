package lld.ratelimiter.full;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;


/**
 * The full limiter: V0 plus
 * <ul>
 *   <li>E1 pluggable algorithms (an {@code EnumMap} of state factories);</li>
 *   <li>E2 several rules per request, all-or-nothing (permits taken from earlier rules are
 *       refunded when a later rule rejects);</li>
 *   <li>E3 weighted cost and a {@link Decision} with remaining and retry-after, plus
 *       per-rule rejection counters;</li>
 *   <li>E4 live per-key policy overrides and idle-state eviction.</li>
 * </ul>
 *
 * <p>Concurrency rule: a state object is only touched inside {@code states.compute(...)}.
 * ConcurrentHashMap runs compute functions for the same key one at a time, so creating,
 * swapping (policy change), acquiring, refunding and evicting a key's state are atomic with
 * respect to each other, and different keys never block each other.
 */
public final class RateLimiterService {

    @FunctionalInterface
    interface StateFactory {
        LimiterState create(LimitPolicy policy, long nowMillis);
    }

    private final Clock clock;
    private final List<Rule> rules = new CopyOnWriteArrayList<>();
    private final Map<String, LimitPolicy> overrides = new ConcurrentHashMap<>();
    private final Map<String, LimiterState> states = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> rejections = new ConcurrentHashMap<>();
    private final Map<Algorithm, StateFactory> factories = new EnumMap<>(Algorithm.class);

    public RateLimiterService(Clock clock) {
        this.clock = clock;
        // E1: one factory per algorithm. A new algorithm is a new LimiterState class plus one put.
        factories.put(Algorithm.TOKEN_BUCKET, TokenBucketState::new);
        factories.put(Algorithm.FIXED_WINDOW, FixedWindowState::new);
        factories.put(Algorithm.SLIDING_WINDOW_LOG, SlidingWindowLogState::new);
        factories.put(Algorithm.SLIDING_WINDOW_COUNTER, SlidingWindowCounterState::new);
    }

    // ---- configuration -------------------------------------------------------------------

    /** Adds or replaces a rule. Rules are evaluated in the order they were first added. */
    public void putRule(Rule rule) {
        rules.removeIf(r -> r.name().equals(rule.name()));
        rules.add(rule);
    }

    /** E4: a different policy for one key of one rule (e.g. a paid client), effective on its next request. */
    public void override(String ruleName, String key, LimitPolicy policy) {
        requireRule(ruleName);
        ValidationException.require(key != null && !key.isBlank(), "Key is required");
        overrides.put(stateKey(ruleName, key), policy);
    }

    public void clearOverride(String ruleName, String key) {
        overrides.remove(stateKey(ruleName, key));
    }

    // ---- the hot path --------------------------------------------------------------------

    public Decision evaluate(Request request) {
        long now = clock.millis();
        List<Rule> snapshot = List.copyOf(rules);
        ValidationException.require(!snapshot.isEmpty(), "No rules configured");

        List<String> acquired = new ArrayList<>();
        Decision tightest = null;
        for (Rule rule : snapshot) {
            String key = stateKey(rule.name(), rule.scope().keyFor(request));
            LimitPolicy policy = overrides.getOrDefault(key, rule.policy());
            if (request.cost() > policy.permits()) {
                refundAll(acquired, request.cost(), now);
                throw new ValidationException("Request cost " + request.cost() + " exceeds rule '"
                        + rule.name() + "' limit of " + policy.permits());
            }

            LimiterState.Outcome[] outcome = new LimiterState.Outcome[1];
            states.compute(key, (k, state) -> {
                LimiterState s = (state == null || !state.policy().equals(policy))
                        ? factoryFor(policy.algorithm()).create(policy, now)   // new key, or policy changed (E4)
                        : state;
                outcome[0] = s.tryAcquire(request.cost(), now);
                return s;
            });

            if (!outcome[0].allowed()) {
                refundAll(acquired, request.cost(), now);
                rejections.computeIfAbsent(rule.name(), r -> new LongAdder()).increment();
                return Decision.reject(rule.name(), outcome[0].retryAfterMillis());
            }
            acquired.add(key);
            if (tightest == null || outcome[0].remaining() < tightest.remaining()) {
                tightest = Decision.allow(rule.name(), outcome[0].remaining());
            }
        }
        return tightest;
    }

    /** Same as {@link #evaluate} but throws, for callers that prefer exceptions. */
    public void acquireOrThrow(Request request) {
        Decision d = evaluate(request);
        if (!d.allowed()) {
            throw new ConflictException("Rate limit exceeded by rule '" + d.rule()
                    + "', retry after " + d.retryAfterSeconds() + " s");
        }
    }

    private void refundAll(List<String> keys, long cost, long now) {
        for (String key : keys) {
            states.computeIfPresent(key, (k, s) -> {
                s.refund(cost, now);
                return s;
            });
        }
    }

    // ---- housekeeping (E4) ---------------------------------------------------------------

    /**
     * Drops states untouched for {@code idle}. Safe against concurrent use: the idle check and
     * removal happen inside computeIfPresent, so a key in use is never removed mid-request.
     * Run it every minute or so via {@link #scheduleEviction}.
     */
    public int evictIdle(Duration idle) {
        long now = clock.millis();
        int[] removed = {0};
        for (String key : states.keySet()) {
            states.computeIfPresent(key, (k, s) -> {
                if (now - s.lastAccessMillis() >= idle.toMillis()) {
                    removed[0]++;
                    return null;
                }
                return s;
            });
        }
        return removed[0];
    }

    /**
     * Runs {@link #evictIdle} every {@code every} on the caller's scheduler. Exceptions are
     * caught so one bad run doesn't cancel the periodic task (a ScheduledExecutorService
     * silently stops a periodic task that throws).
     */
    public ScheduledFuture<?> scheduleEviction(ScheduledExecutorService executor, Duration every, Duration idle) {
        return executor.scheduleAtFixedRate(() -> {
            try {
                evictIdle(idle);
            } catch (RuntimeException e) {
                System.err.println("Idle eviction failed: " + e.getMessage());
            }
        }, every.toMillis(), every.toMillis(), TimeUnit.MILLISECONDS);
    }

    public int trackedKeys() {
        return states.size();
    }

    public long rejections(String ruleName) {
        LongAdder adder = rejections.get(ruleName);
        return adder == null ? 0 : adder.sum();
    }

    private StateFactory factoryFor(Algorithm algorithm) {
        StateFactory factory = factories.get(algorithm);
        if (factory == null) {
            throw new ValidationException("No limiter registered for " + algorithm);
        }
        return factory;
    }

    private void requireRule(String ruleName) {
        ValidationException.require(rules.stream().anyMatch(r -> r.name().equals(ruleName)),
                "Unknown rule: " + ruleName);
    }

    private static String stateKey(String ruleName, String key) {
        return ruleName + "|" + key;
    }
}
