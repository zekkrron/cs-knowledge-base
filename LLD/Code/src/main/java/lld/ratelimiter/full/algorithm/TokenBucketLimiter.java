package lld.ratelimiter.full.algorithm;

import lld.ratelimiter.full.model.Decision;

/**
 * Bucket of {@code permits} tokens refilled at permits per window. Allows short bursts up to
 * the bucket size while holding the average rate, which is why it's the answer to "handle
 * spikes". Refill is computed lazily on each call, so no timer thread.
 */
final class TokenBucketLimiter implements ClientLimiter {

    private final int capacity;
    private final double tokensPerMilli;
    private double tokens;
    private long lastRefill;

    TokenBucketLimiter(int permits, long windowMillis, long nowMillis) {
        this.capacity = permits;
        this.tokensPerMilli = (double) permits / windowMillis;
        this.tokens = permits;
        this.lastRefill = nowMillis;
    }

    @Override
    public Decision tryAcquire(long now, int cost) {
        if (now > lastRefill) {
            tokens = Math.min(capacity, tokens + (now - lastRefill) * tokensPerMilli);
            lastRefill = now;
        }
        if (tokens >= cost) {
            tokens -= cost;
            return Decision.allow((long) Math.floor(tokens));
        }
        return Decision.deny((long) Math.floor(tokens), (long) Math.ceil((cost - tokens) / tokensPerMilli));
    }
}
