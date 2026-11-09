package lld.ratelimiter.full.algorithm;

import lld.ratelimiter.full.model.Decision;

/**
 * Approximate sliding window with two counters: estimate = previous window's count weighted by
 * how much of it still overlaps the sliding window, plus the current count. Memory of fixed
 * window, accuracy close to the log. The usual production answer.
 */
final class SlidingWindowCounterLimiter implements ClientLimiter {

    private final int permits;
    private final long window;
    private long currentStart = Long.MIN_VALUE;
    private long previous;
    private long current;

    SlidingWindowCounterLimiter(int permits, long windowMillis) {
        this.permits = permits;
        this.window = windowMillis;
    }

    @Override
    public Decision tryAcquire(long now, int cost) {
        long start = now - Math.floorMod(now, window);
        if (start != currentStart) {
            boolean adjacent = currentStart != Long.MIN_VALUE && start - currentStart == window;
            previous = adjacent ? current : 0;
            current = 0;
            currentStart = start;
        }
        long elapsed = now - start;
        double estimate = previous * (double) (window - elapsed) / window + current;
        if (estimate + cost <= permits) {
            current += cost;
            return Decision.allow((long) Math.floor(permits - estimate - cost));
        }
        long remaining = Math.max(0, (long) Math.floor(permits - estimate));
        long room = permits - current - cost;
        if (room >= 0 && previous > 0) {
            // the previous window's weight must fall to room / previous
            long target = window - (room * window) / previous;
            return Decision.deny(remaining, target - elapsed);
        }
        return Decision.deny(remaining, window - elapsed); // at least until the next window
    }
}
