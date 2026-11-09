package lld.ratelimiter.v0;

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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import lld.ratelimiter.v0.demo.ManualClock;
import lld.ratelimiter.v0.exception.ValidationException;
import lld.ratelimiter.v0.model.RateLimit;
import lld.ratelimiter.v0.service.RateLimiterService;

class RateLimiterServiceV0Test {

    private final ManualClock clock = new ManualClock(Instant.parse("2026-10-27T10:00:00Z"));
    private final RateLimiterService service = new RateLimiterService(RateLimit.perSeconds(3, 10), clock);

    @Test
    void allowsUpToLimitThenRejectsUntilNextWindow() {
        assertEquals(3, allowed("a", 5));
        clock.advance(Duration.ofMillis(9_999));
        assertFalse(service.tryAcquire("a"));
        clock.advance(Duration.ofMillis(1)); // boundary: new window
        assertEquals(3, allowed("a", 5));
    }

    @Test
    void clientsAreIndependentAndCanHaveTheirOwnLimit() {
        service.configure("premium", RateLimit.perSeconds(5, 10));
        assertEquals(3, allowed("a", 10));
        assertEquals(3, allowed("b", 10));
        assertEquals(5, allowed("premium", 10));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(ValidationException.class, () -> RateLimit.perSeconds(0, 10));
        assertThrows(ValidationException.class, () -> new RateLimit(1, Duration.ZERO));
        assertThrows(ValidationException.class, () -> service.tryAcquire(" "));
    }

    @Test
    void concurrentRequestsFromOneClientGetExactlyTheLimit() throws Exception {
        RateLimiterService s = new RateLimiterService(RateLimit.perSeconds(100, 60), clock);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (s.tryAcquire("hot")) {
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
        assertTrue(s.tryAcquire("other"));
    }

    private int allowed(String client, int requests) {
        int ok = 0;
        for (int i = 0; i < requests; i++) {
            if (service.tryAcquire(client)) {
                ok++;
            }
        }
        return ok;
    }
}
