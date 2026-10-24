package lld.ratelimiter.full;

/**
 * The per-key counter for one algorithm (E1: Strategy).
 *
 * <p>Implementations are NOT thread-safe by themselves. {@link RateLimiterService} only
 * touches a state inside {@code ConcurrentHashMap.compute}, which runs one function at a time
 * per key. That single rule keeps create, acquire, refund and eviction atomic per key.
 */
interface LimiterState {

    LimitPolicy policy();

    /** Tries to take {@code cost} permits at {@code nowMillis}. */
    Outcome tryAcquire(long cost, long nowMillis);

    /** Gives back permits taken moments ago (another rule rejected the same request). */
    void refund(long cost, long nowMillis);

    long lastAccessMillis();

    record Outcome(boolean allowed, long remaining, long retryAfterMillis) {

        static Outcome allowed(long remaining) {
            return new Outcome(true, Math.max(0, remaining), 0);
        }

        static Outcome rejected(long retryAfterMillis) {
            return new Outcome(false, 0, Math.max(1, retryAfterMillis));
        }
    }
}
