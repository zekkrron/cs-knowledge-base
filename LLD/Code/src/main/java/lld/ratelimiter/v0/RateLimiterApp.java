package lld.ratelimiter.v0;

import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;


/**
 * Demo CLI:
 * <pre>
 * configure alice 3 1       (alice: burst 3, refill 1 token/s)
 * request alice             (ALLOWED / REJECTED)
 * advance_ms 1000           (demo clock)
 * </pre>
 */
public final class RateLimiterApp {

    private final ManualClock clock = new ManualClock(Instant.parse("2026-10-24T10:00:00Z"));
    private final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(new RateLimit(5, 1), clock);

    public CommandLineDriver driver() {
        return new CommandLineDriver(System.out)
                .on("configure", a -> {
                    ValidationException.require(a.length == 3, "Usage: configure <client> <capacity> <refillPerSecond>");
                    limiter.configure(a[0], new RateLimit(Long.parseLong(a[1]), Double.parseDouble(a[2])));
                    return "Configured " + a[0];
                })
                .on("request", a -> {
                    ValidationException.require(a.length == 1, "Usage: request <client>");
                    return a[0] + ": " + (limiter.tryAcquire(a[0]) ? "ALLOWED" : "REJECTED");
                })
                .on("advance_ms", a -> {
                    clock.advance(Duration.ofMillis(Long.parseLong(a[0])));
                    return "";
                });
    }

    static final String DEMO = """
            configure alice 3 1
            request alice
            request alice
            request alice
            request alice
            advance_ms 1000
            request alice
            request alice
            request bob
            exit
            """;

    public static void main(String[] args) throws IOException {
        new RateLimiterApp().driver().run(new StringReader(DEMO));
    }
}
