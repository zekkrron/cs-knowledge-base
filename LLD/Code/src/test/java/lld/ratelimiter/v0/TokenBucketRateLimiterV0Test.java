package lld.ratelimiter.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;


class TokenBucketRateLimiterV0Test {

    private final ManualClock clock = new ManualClock(Instant.parse("2026-10-24T10:00:00Z"));

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        var limiter = new TokenBucketRateLimiter(new RateLimit(3, 1), clock);
        assertTrue(limiter.tryAcquire("alice"));
        assertTrue(limiter.tryAcquire("alice"));
        assertTrue(limiter.tryAcquire("alice"));
        assertFalse(limiter.tryAcquire("alice"));
    }

    @Test
    void refillsLazilyWithElapsedTimeAndNeverAboveCapacity() {
        var limiter = new TokenBucketRateLimiter(new RateLimit(2, 1), clock);
        limiter.tryAcquire("alice");
        limiter.tryAcquire("alice");
        assertFalse(limiter.tryAcquire("alice"));
        clock.advance(Duration.ofMillis(1000));
        assertTrue(limiter.tryAcquire("alice"));
        assertFalse(limiter.tryAcquire("alice"));
        clock.advance(Duration.ofHours(1));
        assertTrue(limiter.tryAcquire("alice"));
        assertTrue(limiter.tryAcquire("alice"));
        assertFalse(limiter.tryAcquire("alice"), "refill is capped at capacity");
    }

    @Test
    void clientsAreIndependentAndOverridesApply() {
        var limiter = new TokenBucketRateLimiter(new RateLimit(1, 1), clock);
        limiter.configure("vip", new RateLimit(3, 1));
        assertTrue(limiter.tryAcquire("alice"));
        assertFalse(limiter.tryAcquire("alice"));
        assertTrue(limiter.tryAcquire("bob"));
        assertTrue(limiter.tryAcquire("vip"));
        assertTrue(limiter.tryAcquire("vip"));
        assertTrue(limiter.tryAcquire("vip"));
        assertFalse(limiter.tryAcquire("vip"));
    }

    @Test
    void validatesInput() {
        assertThrows(ValidationException.class, () -> new RateLimit(0, 1));
        assertThrows(ValidationException.class, () -> new RateLimit(1, 0));
        var limiter = new TokenBucketRateLimiter(new RateLimit(1, 1), clock);
        assertThrows(ValidationException.class, () -> limiter.tryAcquire(" "));
    }

    @Test
    void concurrentRequestsNeverExceedCapacity() throws InterruptedException {
        var limiter = new TokenBucketRateLimiter(new RateLimit(50, 1), clock);
        int threads = 16;
        int perThread = 100;
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    if (limiter.tryAcquire("alice")) {
                        allowed.incrementAndGet();
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(50, allowed.get(), "clock is frozen, so exactly capacity requests pass");
    }

    @Test
    void cliDemo() {
        var driver = new RateLimiterApp().driver();
        driver.execute("configure alice 2 1");
        assertEquals("alice: ALLOWED", driver.execute("request alice"));
        assertEquals("alice: ALLOWED", driver.execute("request alice"));
        assertEquals("alice: REJECTED", driver.execute("request alice"));
        driver.execute("advance_ms 1000");
        assertEquals("alice: ALLOWED", driver.execute("request alice"));
    }
}
