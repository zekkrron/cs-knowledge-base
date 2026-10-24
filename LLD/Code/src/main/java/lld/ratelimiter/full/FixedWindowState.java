package lld.ratelimiter.full;

/**
 * Counts per aligned window ([0s,60s), [60s,120s) ...). Cheapest state, but a client can
 * send 2 × permits around a boundary (end of one window + start of the next).
 */
final class FixedWindowState implements LimiterState {

    private final LimitPolicy policy;
    private long windowStart;
    private long count;
    private long lastAccessMillis;

    FixedWindowState(LimitPolicy policy, long nowMillis) {
        this.policy = policy;
        this.windowStart = alignedStart(nowMillis);
        this.lastAccessMillis = nowMillis;
    }

    @Override
    public LimitPolicy policy() {
        return policy;
    }

    @Override
    public Outcome tryAcquire(long cost, long nowMillis) {
        roll(nowMillis);
        lastAccessMillis = nowMillis;
        if (count + cost <= policy.permits()) {
            count += cost;
            return Outcome.allowed(policy.permits() - count);
        }
        return Outcome.rejected(windowStart + policy.windowMillis() - nowMillis);
    }

    @Override
    public void refund(long cost, long nowMillis) {
        roll(nowMillis);
        count = Math.max(0, count - cost);
    }

    @Override
    public long lastAccessMillis() {
        return lastAccessMillis;
    }

    private void roll(long nowMillis) {
        long start = alignedStart(nowMillis);
        if (start != windowStart) {
            windowStart = start;
            count = 0;
        }
    }

    private long alignedStart(long nowMillis) {
        return nowMillis - Math.floorMod(nowMillis, policy.windowMillis());
    }
}
