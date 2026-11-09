package lld.ratelimiter.full.demo;

import java.time.Duration;
import java.time.Instant;

import lld.ratelimiter.full.exception.DomainException;
import lld.ratelimiter.full.model.Algorithm;
import lld.ratelimiter.full.model.Decision;
import lld.ratelimiter.full.model.LimitPolicy;
import lld.ratelimiter.full.service.RateLimiterService;

/** Plain driver: one short scenario per extension, calling the service directly. */
public final class Demo {

    public static void main(String[] args) {
        ManualClock clock = new ManualClock(Instant.parse("2026-10-27T10:00:00Z"));
        Duration tenSeconds = Duration.ofSeconds(10);
        RateLimiterService limiter = new RateLimiterService(
                LimitPolicy.of(Algorithm.FIXED_WINDOW, 5, tenSeconds), clock);

        // E1: the boundary burst of fixed window, then the same traffic on a sliding log
        limiter.configure("log", LimitPolicy.of(Algorithm.SLIDING_WINDOW_LOG, 5, tenSeconds));
        clock.advance(Duration.ofMillis(9_900));
        String fixed = burst(limiter, "fixed", 5);
        String log = burst(limiter, "log", 5);
        clock.advance(Duration.ofMillis(100));
        System.out.println("fixed window around a boundary: " + fixed + " | " + burst(limiter, "fixed", 5));
        System.out.println("sliding log, same traffic:      " + log + " | " + burst(limiter, "log", 5));

        // E2: 2 of 5 used, so the next window has 5 + 3 credits
        limiter.configure("credits", LimitPolicy.withCredits(5, tenSeconds, 10));
        burst(limiter, "credits", 2);
        clock.advance(tenSeconds);
        System.out.println("credits, next window:           " + burst(limiter, "credits", 10));

        // E3: raise a live client's limit
        limiter.configure("fixed", LimitPolicy.of(Algorithm.FIXED_WINDOW, 8, tenSeconds));
        System.out.println("fixed after raising to 8:       " + burst(limiter, "fixed", 10));

        // E4: weighted request on a token bucket, with the 429 details
        limiter.configure("llm", LimitPolicy.of(Algorithm.TOKEN_BUCKET, 1_000, Duration.ofMinutes(1)));
        System.out.println("llm 800 tokens: " + limiter.tryAcquire("llm", 800));
        System.out.println("llm 400 tokens: " + limiter.tryAcquire("llm", 400));
        try {
            limiter.tryAcquire("llm", 5_000);
        } catch (DomainException e) {
            System.out.println("llm 5000 tokens: " + e.getMessage());
        }
        clock.advance(Duration.ofMinutes(5));
        System.out.println("evicted idle clients: " + limiter.evictIdle(Duration.ofMinutes(1)));
    }

    private static String burst(RateLimiterService limiter, String client, int requests) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < requests; i++) {
            Decision d = limiter.tryAcquire(client);
            out.append(d.allowed() ? 'Y' : 'n');
        }
        return out.toString();
    }
}
