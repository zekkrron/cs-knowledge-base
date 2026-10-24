package lld.ratelimiter.v0;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Per-client token buckets with a default limit and per-client overrides.
 *
 * <p>Thread safety in two layers:
 * <ul>
 *   <li>{@code computeIfAbsent} on a ConcurrentHashMap creates exactly one bucket per
 *       client, even if a client's first two requests arrive together;</li>
 *   <li>each bucket synchronises its own refill-check-take.</li>
 * </ul>
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    private final RateLimit defaultLimit;
    private final Map<String, RateLimit> overrides = new ConcurrentHashMap<>();
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public TokenBucketRateLimiter(RateLimit defaultLimit, Clock clock) {
        this.defaultLimit = defaultLimit;
        this.clock = clock;
    }

    /** Takes effect for clients that don't have a bucket yet (V0 keeps it simple; E4 handles live changes). */
    public void configure(String clientId, RateLimit limit) {
        requireClient(clientId);
        overrides.put(clientId, limit);
    }

    @Override
    public boolean tryAcquire(String clientId) {
        requireClient(clientId);
        long now = clock.millis();
        TokenBucket bucket = buckets.computeIfAbsent(clientId,
                id -> new TokenBucket(overrides.getOrDefault(id, defaultLimit), now));
        return bucket.tryConsume(now);
    }

    private static void requireClient(String clientId) {
        ValidationException.require(clientId != null && !clientId.isBlank(), "Client id is required");
    }
}
