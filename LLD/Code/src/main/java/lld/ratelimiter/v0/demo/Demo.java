package lld.ratelimiter.v0.demo;

import java.time.Duration;
import java.time.Instant;

import lld.ratelimiter.v0.model.RateLimit;
import lld.ratelimiter.v0.service.RateLimiterService;

/** Plain driver: 5 requests per 10 s by default, a premium client gets 10. */
public final class Demo {

    public static void main(String[] args) {
        ManualClock clock = new ManualClock(Instant.parse("2026-10-27T10:00:00Z"));
        RateLimiterService limiter = new RateLimiterService(RateLimit.perSeconds(5, 10), clock);
        limiter.configure("premium", RateLimit.perSeconds(10, 10));

        System.out.println("free:    " + burst(limiter, "free", 7));
        System.out.println("premium: " + burst(limiter, "premium", 12));
        clock.advance(Duration.ofSeconds(10));
        System.out.println("free after the window resets: " + burst(limiter, "free", 2));
    }

    private static String burst(RateLimiterService limiter, String client, int requests) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < requests; i++) {
            out.append(limiter.tryAcquire(client) ? 'Y' : 'n');
        }
        return out.toString();
    }
}
