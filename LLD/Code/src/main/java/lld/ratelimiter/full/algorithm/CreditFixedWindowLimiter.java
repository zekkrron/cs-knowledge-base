package lld.ratelimiter.full.algorithm;

import lld.ratelimiter.full.model.Decision;

/**
 * E2 (Atlassian follow-up): fixed window where unused permits carry over as credits, capped at
 * maxCredits. A request first uses the window's permits; once those run out it spends credits.
 * Windows with no traffic at all earn a full window of credits each.
 */
final class CreditFixedWindowLimiter implements ClientLimiter {

    private final int permits;
    private final long window;
    private final int maxCredits;
    private long windowStart = Long.MIN_VALUE;
    private long used;
    private long credits;

    CreditFixedWindowLimiter(int permits, long windowMillis, int maxCredits) {
        this.permits = permits;
        this.window = windowMillis;
        this.maxCredits = maxCredits;
    }

    @Override
    public Decision tryAcquire(long now, int cost) {
        long start = now - Math.floorMod(now, window);
        if (windowStart == Long.MIN_VALUE) {
            windowStart = start;
        } else if (start != windowStart) {
            long idleWindows = (start - windowStart) / window - 1;
            // min() keeps a long gap from overflowing; anything past maxCredits is capped anyway
            long earned = (permits - used) + Math.min(idleWindows, maxCredits) * (long) permits;
            credits = Math.min(maxCredits, credits + earned);
            windowStart = start;
            used = 0;
        }
        long fromWindow = permits - used;
        if (cost <= fromWindow + credits) {
            long take = Math.min(cost, fromWindow);
            used += take;
            credits -= cost - take;
            return Decision.allow(permits - used + credits);
        }
        return Decision.deny(fromWindow + credits, windowStart + window - now);
    }
}
