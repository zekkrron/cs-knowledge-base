package lld.ratelimiter.v0;


/**
 * Token bucket settings: up to {@code capacity} requests in a burst, refilled at
 * {@code refillPerSecond}. "100 per minute" = capacity 100, refill 100/60 per second.
 */
public record RateLimit(long capacity, double refillPerSecond) {

    public RateLimit {
        ValidationException.require(capacity > 0, "Capacity must be positive");
        ValidationException.require(refillPerSecond > 0, "Refill rate must be positive");
    }
}
