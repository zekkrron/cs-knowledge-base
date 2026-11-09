package lld.ratelimiter.full.algorithm;

import lld.ratelimiter.full.model.LimitPolicy;

/** E1: picks the implementation for a policy. Adding an algorithm is one class plus one case. */
public final class LimiterFactory {

    private LimiterFactory() {
    }

    public static ClientLimiter create(LimitPolicy policy, long nowMillis) {
        int permits = policy.permits();
        long window = policy.window().toMillis();
        return switch (policy.algorithm()) {
            case FIXED_WINDOW -> new FixedWindowLimiter(permits, window);
            case SLIDING_WINDOW_LOG -> new SlidingWindowLogLimiter(permits, window);
            case SLIDING_WINDOW_COUNTER -> new SlidingWindowCounterLimiter(permits, window);
            case TOKEN_BUCKET -> new TokenBucketLimiter(permits, window, nowMillis);
            case FIXED_WINDOW_WITH_CREDITS -> new CreditFixedWindowLimiter(permits, window, policy.maxCredits());
        };
    }
}
