package lld.ratelimiter.full.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lld.ratelimiter.full.algorithm.ClientLimiter;
import lld.ratelimiter.full.algorithm.LimiterFactory;
import lld.ratelimiter.full.exception.ValidationException;
import lld.ratelimiter.full.model.Decision;
import lld.ratelimiter.full.model.LimitPolicy;

/**
 * Per-client limiter.
 *
 * <ul>
 *   <li>E1: the policy names the algorithm; {@link LimiterFactory} builds it.</li>
 *   <li>E2: FIXED_WINDOW_WITH_CREDITS carries unused permits over.</li>
 *   <li>E3: {@link #configure} works on live traffic, and idle clients can be evicted.</li>
 *   <li>E4: requests carry a cost and get a {@link Decision} back.</li>
 * </ul>
 *
 * <p>Thread safety: each call runs inside {@code compute} on the client's key, so the policy
 * check, the counting and the eviction check for one client never interleave.
 */
public final class RateLimiterService {

    /** One client's state. The policy is kept so a config change can be detected. */
    private static final class ClientState {
        final LimitPolicy policy;
        final ClientLimiter limiter;
        long lastSeenMillis;

        ClientState(LimitPolicy policy, ClientLimiter limiter) {
            this.policy = policy;
            this.limiter = limiter;
        }
    }

    private final LimitPolicy defaultPolicy;
    private final Clock clock;
    private final Map<String, LimitPolicy> policies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientState> clients = new ConcurrentHashMap<>();

    public RateLimiterService(LimitPolicy defaultPolicy, Clock clock) {
        ValidationException.require(defaultPolicy != null, "Default policy is required");
        this.defaultPolicy = defaultPolicy;
        this.clock = clock;
    }

    /**
     * E3: sets or changes a client's policy, also while it has traffic. The next request sees
     * the new policy and starts from fresh counters (simple and predictable; carrying usage over
     * between different algorithms has no single right answer).
     */
    public void configure(String clientId, LimitPolicy policy) {
        requireClient(clientId);
        ValidationException.require(policy != null, "Policy is required");
        policies.put(clientId, policy);
    }

    public Decision tryAcquire(String clientId) {
        return tryAcquire(clientId, 1);
    }

    /** E4: cost is how many permits this request uses, e.g. LLM tokens. */
    public Decision tryAcquire(String clientId, int cost) {
        requireClient(clientId);
        ValidationException.require(cost > 0, "cost must be positive");
        LimitPolicy policy = policies.getOrDefault(clientId, defaultPolicy);
        // a request bigger than the whole limit would wait forever: reject it as invalid
        ValidationException.require(cost <= policy.permits(),
                "cost " + cost + " exceeds the limit of " + policy.permits());
        long now = clock.millis();
        Decision[] result = new Decision[1];
        clients.compute(clientId, (id, state) -> {
            ClientState current = state != null && state.policy.equals(policy)
                    ? state
                    : new ClientState(policy, LimiterFactory.create(policy, now));
            current.lastSeenMillis = now;
            result[0] = current.limiter.tryAcquire(now, cost);
            return current;
        });
        return result[0];
    }

    /**
     * E3: drops clients with no request for {@code idle}. Use idle of at least the longest
     * window so an evicted client restarts in the same state it would be in anyway (credits are
     * the exception: they are lost, which errs on the strict side).
     */
    public int evictIdle(Duration idle) {
        long cutoff = clock.millis() - idle.toMillis();
        AtomicInteger removed = new AtomicInteger();
        for (String id : clients.keySet()) {
            clients.computeIfPresent(id, (key, state) -> {
                if (state.lastSeenMillis <= cutoff) {
                    removed.incrementAndGet();
                    return null;
                }
                return state;
            });
        }
        return removed.get();
    }

    /** E3: runs {@link #evictIdle} on the caller's scheduler; cancel the returned future to stop. */
    public ScheduledFuture<?> scheduleEviction(ScheduledExecutorService scheduler, Duration every, Duration idle) {
        ValidationException.require(every.toMillis() > 0, "every must be at least 1 ms");
        return scheduler.scheduleAtFixedRate(() -> evictIdle(idle), every.toMillis(), every.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public int trackedClients() {
        return clients.size();
    }

    private static void requireClient(String clientId) {
        ValidationException.require(clientId != null && !clientId.isBlank(), "Client id is required");
    }
}
