package lld.ratelimiter.full;

/**
 * Two counters (current and previous window) and a weighted estimate:
 * {@code estimate = previous × (1 − elapsedFractionOfCurrent) + current}.
 *
 * <p>Fixes most of the fixed-window boundary burst with O(1) memory; the price is that the
 * estimate assumes the previous window's requests were spread evenly.
 */
final class SlidingWindowCounterState implements LimiterState {

    private final LimitPolicy policy;
    private long windowStart;
    private long current;
    private long previous;
    private long lastAccessMillis;

    SlidingWindowCounterState(LimitPolicy policy, long nowMillis) {
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
        double estimate = estimate(nowMillis);
        if (estimate + cost <= policy.permits()) {
            current += cost;
            return Outcome.allowed((long) Math.floor(policy.permits() - estimate - cost));
        }
        return Outcome.rejected(retryHint(cost, nowMillis));
    }

    @Override
    public void refund(long cost, long nowMillis) {
        roll(nowMillis);
        current = Math.max(0, current - cost);
    }

    @Override
    public long lastAccessMillis() {
        return lastAccessMillis;
    }

    private double estimate(long nowMillis) {
        double elapsedFraction = (double) (nowMillis - windowStart) / policy.windowMillis();
        return previous * (1 - elapsedFraction) + current;
    }

    /** When the previous window's weight will have decayed enough; otherwise the next window. */
    private long retryHint(long cost, long nowMillis) {
        long room = policy.permits() - current - cost;
        if (room >= 0 && previous > 0) {
            double fractionNeeded = 1 - (double) room / previous;
            long at = windowStart + (long) Math.ceil(fractionNeeded * policy.windowMillis());
            return Math.max(1, at - nowMillis);
        }
        return windowStart + policy.windowMillis() - nowMillis;
    }

    private void roll(long nowMillis) {
        long start = alignedStart(nowMillis);
        if (start == windowStart) {
            return;
        }
        previous = (start - windowStart == policy.windowMillis()) ? current : 0;
        current = 0;
        windowStart = start;
    }

    private long alignedStart(long nowMillis) {
        return nowMillis - Math.floorMod(nowMillis, policy.windowMillis());
    }
}
