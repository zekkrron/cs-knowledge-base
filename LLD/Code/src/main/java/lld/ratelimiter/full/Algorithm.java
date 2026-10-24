package lld.ratelimiter.full;


/** E1: the algorithms a policy can pick. Each maps to a {@link LimiterState} factory. */
public enum Algorithm {
    TOKEN_BUCKET,
    FIXED_WINDOW,
    SLIDING_WINDOW_LOG,
    SLIDING_WINDOW_COUNTER;

    public static Algorithm parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown algorithm: " + raw);
        }
    }
}
