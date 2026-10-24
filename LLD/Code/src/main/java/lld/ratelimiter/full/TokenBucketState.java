package lld.ratelimiter.full;

/** Bursts up to {@code permits}, refilled continuously at permits / window. Lazy refill. */
final class TokenBucketState implements LimiterState {

    private final LimitPolicy policy;
    private final double refillPerMilli;
    private double tokens;
    private long lastRefillMillis;
    private long lastAccessMillis;

    TokenBucketState(LimitPolicy policy, long nowMillis) {
        this.policy = policy;
        this.refillPerMilli = (double) policy.permits() / policy.windowMillis();
        this.tokens = policy.permits();
        this.lastRefillMillis = nowMillis;
        this.lastAccessMillis = nowMillis;
    }

    @Override
    public LimitPolicy policy() {
        return policy;
    }

    @Override
    public Outcome tryAcquire(long cost, long nowMillis) {
        refill(nowMillis);
        lastAccessMillis = nowMillis;
        if (tokens >= cost) {
            tokens -= cost;
            return Outcome.allowed((long) Math.floor(tokens));
        }
        long waitMillis = (long) Math.ceil((cost - tokens) / refillPerMilli);
        return Outcome.rejected(waitMillis);
    }

    @Override
    public void refund(long cost, long nowMillis) {
        refill(nowMillis);
        tokens = Math.min(policy.permits(), tokens + cost);
    }

    @Override
    public long lastAccessMillis() {
        return lastAccessMillis;
    }

    private void refill(long nowMillis) {
        long elapsed = nowMillis - lastRefillMillis;
        if (elapsed > 0) {
            tokens = Math.min(policy.permits(), tokens + elapsed * refillPerMilli);
            lastRefillMillis = nowMillis;
        }
    }
}
