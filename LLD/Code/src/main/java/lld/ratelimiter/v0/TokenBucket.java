package lld.ratelimiter.v0;

/**
 * One client's bucket. Refill is lazy: tokens are recomputed from elapsed time when a
 * request arrives, so there is no background thread per client.
 *
 * <p>{@code synchronized} makes "refill, check, take" one atomic step for this bucket.
 * Different clients have different buckets, so they never contend with each other.
 */
final class TokenBucket {

    private final long capacity;
    private final double refillPerMilli;
    private double tokens;
    private long lastRefillMillis;

    TokenBucket(RateLimit limit, long nowMillis) {
        this.capacity = limit.capacity();
        this.refillPerMilli = limit.refillPerSecond() / 1000.0;
        this.tokens = limit.capacity();
        this.lastRefillMillis = nowMillis;
    }

    synchronized boolean tryConsume(long nowMillis) {
        refill(nowMillis);
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    private void refill(long nowMillis) {
        long elapsed = nowMillis - lastRefillMillis;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillPerMilli);
            lastRefillMillis = nowMillis;
        }
    }
}
