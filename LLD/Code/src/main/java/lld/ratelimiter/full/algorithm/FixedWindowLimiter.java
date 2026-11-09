package lld.ratelimiter.full.algorithm;

import lld.ratelimiter.full.model.Decision;

/**
 * V0's algorithm with cost and a Decision. Windows are aligned to multiples of the window
 * length. Cheapest (two numbers per client) but allows up to 2x the limit across a boundary.
 */
final class FixedWindowLimiter implements ClientLimiter {

    private final int permits;
    private final long window;
    private long windowStart = Long.MIN_VALUE;
    private long used;

    FixedWindowLimiter(int permits, long windowMillis) {
        this.permits = permits;
        this.window = windowMillis;
    }

    @Override
    public Decision tryAcquire(long now, int cost) {
        long start = now - Math.floorMod(now, window);
        if (start != windowStart) {
            windowStart = start;
            used = 0;
        }
        if (used + cost <= permits) {
            used += cost;
            return Decision.allow(permits - used);
        }
        return Decision.deny(permits - used, windowStart + window - now);
    }
}
