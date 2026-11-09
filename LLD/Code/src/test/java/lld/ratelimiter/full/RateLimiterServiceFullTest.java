package lld.ratelimiter.full;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import lld.ratelimiter.full.demo.ManualClock;
import lld.ratelimiter.full.exception.ValidationException;
import lld.ratelimiter.full.model.Algorithm;
import lld.ratelimiter.full.model.Decision;
import lld.ratelimiter.full.model.LimitPolicy;
import lld.ratelimiter.full.service.RateLimiterService;

class RateLimiterServiceFullTest {

    private static final Duration TEN_S = Duration.ofSeconds(10);

    private final ManualClock clock = new ManualClock(Instant.parse("2026-10-27T10:00:00Z"));

    private RateLimiterService service(LimitPolicy policy) {
        return new RateLimiterService(policy, clock);
    }

    // ---------- E1: algorithms ----------

    @Test
    void e1FixedWindowLetsABurstThroughAtTheBoundarySlidingLogDoesNot() {
        RateLimiterService fixed = service(LimitPolicy.of(Algorithm.FIXED_WINDOW, 5, TEN_S));
        RateLimiterService log = service(LimitPolicy.of(Algorithm.SLIDING_WINDOW_LOG, 5, TEN_S));
        clock.advance(Duration.ofMillis(9_900));
        assertEquals(5, allowed(fixed, "c", 5));
        assertEquals(5, allowed(log, "c", 5));
        clock.advance(Duration.ofMillis(100));
        assertEquals(5, allowed(fixed, "c", 5));   // 10 within 100 ms
        Decision d = log.tryAcquire("c");
        assertFalse(d.allowed());
        assertEquals(Duration.ofMillis(9_900), d.retryAfter()); // oldest entry expires at 19.9 s
    }

    @Test
    void e1SlidingCounterWeightsThePreviousWindow() {
        RateLimiterService s = service(LimitPolicy.of(Algorithm.SLIDING_WINDOW_COUNTER, 10, TEN_S));
        assertEquals(10, allowed(s, "c", 10));
        clock.advance(TEN_S);                       // previous = 10, weight 1.0
        Decision d = s.tryAcquire("c");
        assertFalse(d.allowed());
        assertEquals(Duration.ofSeconds(1), d.retryAfter());
        clock.advance(Duration.ofSeconds(5));       // weight 0.5, estimate 5
        assertEquals(5, allowed(s, "c", 10));
    }

    @Test
    void e1TokenBucketAllowsBurstThenRefillsAtTheAverageRate() {
        RateLimiterService s = service(LimitPolicy.of(Algorithm.TOKEN_BUCKET, 10, TEN_S));
        assertEquals(10, allowed(s, "c", 10));
        Decision d = s.tryAcquire("c");
        assertFalse(d.allowed());
        assertEquals(Duration.ofSeconds(1), d.retryAfter()); // one token per second
        clock.advance(Duration.ofMillis(500));
        assertFalse(s.tryAcquire("c").allowed());
        clock.advance(Duration.ofMillis(500));
        assertTrue(s.tryAcquire("c").allowed());
    }

    // ---------- E2: credits ----------

    @Test
    void e2UnusedPermitsCarryOverAndAreCapped() {
        RateLimiterService s = service(LimitPolicy.withCredits(5, TEN_S, 7));
        assertEquals(2, allowed(s, "c", 2));        // 3 unused
        clock.advance(TEN_S);
        assertEquals(8, allowed(s, "c", 20));       // 5 + 3 credits
        clock.advance(Duration.ofSeconds(30));      // two fully idle windows earn 10, capped at 7
        assertEquals(12, allowed(s, "c", 20));
    }

    @Test
    void e2RemainingIncludesCredits() {
        RateLimiterService s = service(LimitPolicy.withCredits(5, TEN_S, 10));
        clock.advance(TEN_S.multipliedBy(3));       // first request starts the clock: no credits yet
        assertEquals(4, s.tryAcquire("c").remaining());
        clock.advance(TEN_S);
        assertEquals(4 + 4, s.tryAcquire("c").remaining()); // 4 credits + 4 left in window
    }

    // ---------- E3: runtime config and eviction ----------

    @Test
    void e3ChangingALiveClientsPolicyTakesEffectOnTheNextRequest() {
        RateLimiterService s = service(LimitPolicy.of(Algorithm.FIXED_WINDOW, 2, TEN_S));
        assertEquals(2, allowed(s, "c", 5));
        s.configure("c", LimitPolicy.of(Algorithm.TOKEN_BUCKET, 5, TEN_S));
        assertEquals(5, allowed(s, "c", 10));
        assertEquals(2, allowed(s, "other", 5));    // others keep the default
    }

    @Test
    void e3EvictsOnlyIdleClients() {
        RateLimiterService s = service(LimitPolicy.of(Algorithm.FIXED_WINDOW, 2, TEN_S));
        s.tryAcquire("idle");
        clock.advance(Duration.ofMinutes(2));
        s.tryAcquire("active");
        assertEquals(1, s.evictIdle(Duration.ofMinutes(1)));
        assertEquals(1, s.trackedClients());
    }

    @Test
    void e3ScheduledEvictionRunsOnTheGivenScheduler() throws Exception {
        RateLimiterService s = service(LimitPolicy.of(Algorithm.FIXED_WINDOW, 2, TEN_S));
        s.tryAcquire("a");
        s.tryAcquire("b");
        clock.advance(Duration.ofMinutes(2));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            ScheduledFuture<?> job = s.scheduleEviction(scheduler, Duration.ofMillis(10), Duration.ofMinutes(1));
            long deadline = System.currentTimeMillis() + 2_000;
            while (s.trackedClients() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            job.cancel(false);
            assertEquals(0, s.trackedClients());
        } finally {
            scheduler.shutdownNow();
        }
    }

    // ---------- E4: cost and Decision ----------

    @Test
    void e4WeightedRequestsAndDecisionDetails() {
        RateLimiterService s = service(LimitPolicy.of(Algorithm.TOKEN_BUCKET, 100, Duration.ofMinutes(1)));
        Decision first = s.tryAcquire("agent", 60);
        assertTrue(first.allowed());
        assertEquals(40, first.remaining());
        Decision second = s.tryAcquire("agent", 50);
        assertFalse(second.allowed());
        assertEquals(40, second.remaining());
        assertEquals(Duration.ofSeconds(6), second.retryAfter()); // 10 tokens at 100 per minute
        assertThrows(ValidationException.class, () -> s.tryAcquire("agent", 101));
        assertThrows(ValidationException.class, () -> s.tryAcquire("agent", 0));
    }

    @Test
    void e4FixedWindowRetryAfterIsTimeToNextWindow() {
        RateLimiterService s = service(LimitPolicy.of(Algorithm.FIXED_WINDOW, 3, TEN_S));
        clock.advance(Duration.ofSeconds(4));
        assertEquals(2, s.tryAcquire("c", 1).remaining());
        Decision d = s.tryAcquire("c", 3);
        assertFalse(d.allowed());
        assertEquals(Duration.ofSeconds(6), d.retryAfter());
    }

    // ---------- validation and concurrency ----------

    @Test
    void rejectsBadPolicies() {
        assertThrows(ValidationException.class, () -> LimitPolicy.of(Algorithm.FIXED_WINDOW, 0, TEN_S));
        assertThrows(ValidationException.class, () -> LimitPolicy.of(Algorithm.FIXED_WINDOW, 1, Duration.ZERO));
        assertThrows(ValidationException.class, () -> new LimitPolicy(Algorithm.TOKEN_BUCKET, 5, TEN_S, 3));
        assertThrows(ValidationException.class, () -> LimitPolicy.withCredits(5, TEN_S, -1));
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void concurrentRequestsFromOneClientGetExactlyTheLimit(Algorithm algorithm) throws Exception {
        RateLimiterService s = service(LimitPolicy.of(algorithm, 100, Duration.ofMinutes(1)));
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (s.tryAcquire("hot").allowed()) {
                    ok.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertEquals(100, ok.get());
    }

    private static int allowed(RateLimiterService s, String client, int requests) {
        int ok = 0;
        for (int i = 0; i < requests; i++) {
            if (s.tryAcquire(client).allowed()) {
                ok++;
            }
        }
        return ok;
    }
}
