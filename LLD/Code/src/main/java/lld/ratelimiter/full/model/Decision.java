package lld.ratelimiter.full.model;

import java.time.Duration;

/**
 * E4: what a gateway needs to answer 429 properly: allowed or not, permits left
 * (X-RateLimit-Remaining) and how long to wait (Retry-After). retryAfter is ZERO when allowed.
 */
public record Decision(boolean allowed, long remaining, Duration retryAfter) {

    public static Decision allow(long remaining) {
        return new Decision(true, remaining, Duration.ZERO);
    }

    public static Decision deny(long remaining, long retryAfterMillis) {
        return new Decision(false, remaining, Duration.ofMillis(Math.max(1, retryAfterMillis)));
    }
}
