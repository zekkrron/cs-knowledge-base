package lld.ratelimiter.full;

import java.time.Duration;


/**
 * "{@code permits} per {@code window}, enforced with {@code algorithm}".
 *
 * <p>For the token bucket, permits is the bucket capacity (the burst) and the refill rate is
 * permits / window. For window algorithms, permits is the maximum per window.
 */
public record LimitPolicy(Algorithm algorithm, long permits, Duration window) {

    public LimitPolicy {
        ValidationException.require(algorithm != null, "Algorithm is required");
        ValidationException.require(permits > 0, "Permits must be positive");
        ValidationException.require(window != null && window.toMillis() > 0, "Window must be at least 1 ms");
    }

    public long windowMillis() {
        return window.toMillis();
    }
}
