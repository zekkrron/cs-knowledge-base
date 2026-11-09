package lld.ratelimiter.full.model;

/** E1: the algorithms interviewers ask to compare and swap. E2 adds the credits variant. */
public enum Algorithm {
    FIXED_WINDOW,
    SLIDING_WINDOW_LOG,
    SLIDING_WINDOW_COUNTER,
    TOKEN_BUCKET,
    FIXED_WINDOW_WITH_CREDITS
}
