package lld.ratelimiter.v0.model;

import java.time.Duration;

import lld.ratelimiter.v0.exception.ValidationException;

/** "X requests per Y seconds", the shape every reported prompt starts with. */
public record RateLimit(int maxRequests, Duration window) {

    public RateLimit {
        ValidationException.require(maxRequests > 0, "maxRequests must be positive");
        ValidationException.require(window != null && window.toMillis() > 0, "window must be at least 1 ms");
    }

    public static RateLimit perSeconds(int maxRequests, long seconds) {
        return new RateLimit(maxRequests, Duration.ofSeconds(seconds));
    }
}
