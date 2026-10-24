package lld.ratelimiter.full;

import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;


/**
 * Demo CLI for the full limiter:
 * <pre>
 * rule per-user CLIENT TOKEN_BUCKET 5 1000           (5 per second per client, bursty)
 * rule per-endpoint ENDPOINT FIXED_WINDOW 100 60000  (100 per minute per endpoint)
 * override per-user alice SLIDING_WINDOW_LOG 2 1000
 * request alice /search [cost]
 * advance_ms 500
 * evict_idle_ms 60000
 * </pre>
 */
public final class RateLimiterApp {

    private final ManualClock clock = new ManualClock(Instant.parse("2026-10-24T10:00:00Z"));
    private final RateLimiterService service = new RateLimiterService(clock);

    public CommandLineDriver driver() {
        return new CommandLineDriver(System.out)
                .on("rule", a -> {
                    ValidationException.require(a.length == 5,
                            "Usage: rule <name> <scope> <algorithm> <permits> <windowMs>");
                    service.putRule(new Rule(a[0], Scope.parse(a[1]), policy(a[2], a[3], a[4])));
                    return "Rule " + a[0] + " set";
                })
                .on("override", a -> {
                    ValidationException.require(a.length == 5,
                            "Usage: override <rule> <key> <algorithm> <permits> <windowMs>");
                    service.override(a[0], a[1], policy(a[2], a[3], a[4]));
                    return "Override " + a[0] + "/" + a[1] + " set";
                })
                .on("request", a -> {
                    ValidationException.require(a.length == 2 || a.length == 3,
                            "Usage: request <client> <endpoint> [cost]");
                    long cost = a.length == 3 ? number(a[2]) : 1;
                    Decision d = service.evaluate(new Request(a[0], a[1], cost));
                    return d.allowed()
                            ? "ALLOWED (tightest rule=" + d.rule() + ", remaining=" + d.remaining() + ")"
                            : "REJECTED by " + d.rule() + ", retry after " + d.retryAfter().toMillis() + " ms";
                })
                .on("advance_ms", a -> {
                    clock.advance(Duration.ofMillis(number(a[0])));
                    return "";
                })
                .on("evict_idle_ms", a -> "Evicted " + service.evictIdle(Duration.ofMillis(number(a[0]))) + " idle keys");
    }

    private static LimitPolicy policy(String algorithm, String permits, String windowMs) {
        return new LimitPolicy(Algorithm.parse(algorithm), number(permits), Duration.ofMillis(number(windowMs)));
    }

    private static long number(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new ValidationException("Not a number: " + raw);
        }
    }

    static final String DEMO = """
            rule per-user CLIENT TOKEN_BUCKET 3 1000
            rule per-endpoint ENDPOINT FIXED_WINDOW 4 60000
            request alice /search
            request alice /search
            request bob /search
            request bob /search
            request carol /search
            advance_ms 1000
            override per-user alice SLIDING_WINDOW_LOG 1 1000
            request alice /orders
            request alice /orders
            request alice /orders 5
            evict_idle_ms 1000
            exit
            """;

    public static void main(String[] args) throws IOException {
        new RateLimiterApp().driver().run(new StringReader(DEMO));
    }
}
