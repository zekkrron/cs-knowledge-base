package lld.ratelimiter.full;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Keeps a timestamp (and cost) for every accepted request in the last window. Exact, but
 * memory grows with the limit: use it for low limits on valuable actions (OTP sends,
 * password resets), not for 10k requests per minute.
 */
final class SlidingWindowLogState implements LimiterState {

    private record Entry(long atMillis, long cost) {
    }

    private final LimitPolicy policy;
    private final Deque<Entry> log = new ArrayDeque<>();
    private long used;
    private long lastAccessMillis;

    SlidingWindowLogState(LimitPolicy policy, long nowMillis) {
        this.policy = policy;
        this.lastAccessMillis = nowMillis;
    }

    @Override
    public LimitPolicy policy() {
        return policy;
    }

    @Override
    public Outcome tryAcquire(long cost, long nowMillis) {
        evictOlderThan(nowMillis - policy.windowMillis());
        lastAccessMillis = nowMillis;
        if (used + cost <= policy.permits()) {
            log.addLast(new Entry(nowMillis, cost));
            used += cost;
            return Outcome.allowed(policy.permits() - used);
        }
        return Outcome.rejected(millisUntilRoomFor(cost, nowMillis));
    }

    @Override
    public void refund(long cost, long nowMillis) {
        // A refund follows our own acquire moments earlier, so the newest entry is ours or, if another
        // request slipped in between, one of equal cost and a slightly newer timestamp. Removing that
        // one keeps the count exact and is at worst a few ms lenient on expiry.
        Entry last = log.peekLast();
        if (last != null && last.cost() == cost) {
            log.removeLast();
            used -= cost;
        }
    }

    @Override
    public long lastAccessMillis() {
        return lastAccessMillis;
    }

    private void evictOlderThan(long cutoff) {
        while (!log.isEmpty() && log.peekFirst().atMillis() <= cutoff) {
            used -= log.removeFirst().cost();
        }
    }

    /** Walks the oldest entries until enough would have expired to fit {@code cost}. */
    private long millisUntilRoomFor(long cost, long nowMillis) {
        long freed = 0;
        Iterator<Entry> it = log.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            freed += e.cost();
            if (used - freed + cost <= policy.permits()) {
                return e.atMillis() + policy.windowMillis() - nowMillis + 1;
            }
        }
        return policy.windowMillis();
    }
}
