package lld.ratelimiter.full;

import java.time.Duration;

/**
 * E3: the answer an API layer needs to build a response.
 *
 * <ul>
 *   <li>allowed → proceed; {@code remaining} is the tightest headroom across all rules;</li>
 *   <li>rejected → HTTP 429 with {@code Retry-After} = {@code retryAfter} rounded up to seconds;
 *       {@code rule} names the rule that said no.</li>
 * </ul>
 */
public record Decision(boolean allowed, String rule, long remaining, Duration retryAfter) {

    static Decision allow(String rule, long remaining) {
        return new Decision(true, rule, remaining, Duration.ZERO);
    }

    static Decision reject(String rule, long retryAfterMillis) {
        return new Decision(false, rule, 0, Duration.ofMillis(Math.max(1, retryAfterMillis)));
    }

    public long retryAfterSeconds() {
        return (retryAfter.toMillis() + 999) / 1000;
    }
}
