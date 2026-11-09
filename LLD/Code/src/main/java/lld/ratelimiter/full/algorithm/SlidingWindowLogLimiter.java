package lld.ratelimiter.full.algorithm;

import java.util.ArrayDeque;
import java.util.Deque;

import lld.ratelimiter.full.model.Decision;

/**
 * Exact: keeps every accepted request of the last window as (time, cost). No boundary burst,
 * but memory grows with the limit (one entry per request), so it suits small limits.
 */
final class SlidingWindowLogLimiter implements ClientLimiter {

    private final int permits;
    private final long window;
    private final Deque<long[]> log = new ArrayDeque<>();
    private long inWindow;

    SlidingWindowLogLimiter(int permits, long windowMillis) {
        this.permits = permits;
        this.window = windowMillis;
    }

    @Override
    public Decision tryAcquire(long now, int cost) {
        while (!log.isEmpty() && log.peekFirst()[0] <= now - window) {
            inWindow -= log.pollFirst()[1];
        }
        if (inWindow + cost <= permits) {
            log.addLast(new long[] {now, cost});
            inWindow += cost;
            return Decision.allow(permits - inWindow);
        }
        // wait until enough of the oldest entries expire to make room
        long freed = 0;
        for (long[] entry : log) {
            freed += entry[1];
            if (inWindow - freed + cost <= permits) {
                return Decision.deny(permits - inWindow, entry[0] + window - now);
            }
        }
        return Decision.deny(permits - inWindow, window); // unreachable while cost <= permits
    }
}
