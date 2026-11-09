package lld.ratelimiter.v0.algorithm;

import java.time.Instant;

import lld.ratelimiter.v0.model.RateLimit;

/**
 * Windows aligned to multiples of the window length since the epoch: with 5 per 10 s, the
 * windows are [0,10), [10,20) and so on. Known weakness: up to 2x the limit across a boundary
 * (5 at 9.9 s and 5 at 10.0 s), which is why the interviewer then asks for a sliding window.
 */
public final class FixedWindowLimiter implements ClientLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private long windowStart = Long.MIN_VALUE;
    private int count;

    public FixedWindowLimiter(RateLimit limit) {
        this.maxRequests = limit.maxRequests();
        this.windowMillis = limit.window().toMillis();
    }

    @Override
    public boolean tryAcquire(Instant now) {
        long millis = now.toEpochMilli();
        long start = millis - Math.floorMod(millis, windowMillis);
        if (start != windowStart) {
            windowStart = start;
            count = 0;
        }
        if (count < maxRequests) {
            count++;
            return true;
        }
        return false;
    }
}
