package lld.ratelimiter.full.model;

import java.time.Duration;

import lld.ratelimiter.full.exception.ValidationException;

/**
 * "permits per window" plus the algorithm that enforces it. For TOKEN_BUCKET, permits is the
 * bucket size and the refill rate is permits per window. maxCredits is only used by
 * FIXED_WINDOW_WITH_CREDITS (E2) and must be 0 otherwise.
 */
public record LimitPolicy(Algorithm algorithm, int permits, Duration window, int maxCredits) {

    public LimitPolicy {
        ValidationException.require(algorithm != null, "Algorithm is required");
        ValidationException.require(permits > 0, "permits must be positive");
        ValidationException.require(window != null && window.toMillis() > 0, "window must be at least 1 ms");
        ValidationException.require(maxCredits >= 0, "maxCredits can't be negative");
        ValidationException.require(maxCredits == 0 || algorithm == Algorithm.FIXED_WINDOW_WITH_CREDITS,
                "maxCredits only applies to FIXED_WINDOW_WITH_CREDITS");
    }

    public static LimitPolicy of(Algorithm algorithm, int permits, Duration window) {
        return new LimitPolicy(algorithm, permits, window, 0);
    }

    public static LimitPolicy withCredits(int permits, Duration window, int maxCredits) {
        return new LimitPolicy(Algorithm.FIXED_WINDOW_WITH_CREDITS, permits, window, maxCredits);
    }
}
