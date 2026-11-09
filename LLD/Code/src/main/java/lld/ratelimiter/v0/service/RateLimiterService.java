package lld.ratelimiter.v0.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lld.ratelimiter.v0.algorithm.ClientLimiter;
import lld.ratelimiter.v0.algorithm.FixedWindowLimiter;
import lld.ratelimiter.v0.exception.ValidationException;
import lld.ratelimiter.v0.model.RateLimit;

/**
 * Per-client limiter. Clients without their own config get the default limit.
 *
 * <p>Thread safety: each call runs inside {@code compute} on that client's key, so two
 * requests from one client can't both see "4 used" and both take the 5th permit. Different
 * clients don't block each other (apart from rare hash-bin sharing).
 */
public final class RateLimiterService {

    private final RateLimit defaultLimit;
    private final Clock clock;
    private final Map<String, RateLimit> limits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientLimiter> limiters = new ConcurrentHashMap<>();

    public RateLimiterService(RateLimit defaultLimit, Clock clock) {
        ValidationException.require(defaultLimit != null, "Default limit is required");
        this.defaultLimit = defaultLimit;
        this.clock = clock;
    }

    /** Sets a client's own limit. Configure clients before traffic; changing live limits is E3. */
    public void configure(String clientId, RateLimit limit) {
        requireClient(clientId);
        ValidationException.require(limit != null, "Limit is required");
        limits.put(clientId, limit);
        limiters.remove(clientId);
    }

    public boolean tryAcquire(String clientId) {
        requireClient(clientId);
        Instant now = clock.instant();
        boolean[] allowed = new boolean[1];
        limiters.compute(clientId, (id, limiter) -> {
            ClientLimiter current = limiter != null
                    ? limiter
                    : new FixedWindowLimiter(limits.getOrDefault(id, defaultLimit));
            allowed[0] = current.tryAcquire(now);
            return current;
        });
        return allowed[0];
    }

    private static void requireClient(String clientId) {
        ValidationException.require(clientId != null && !clientId.isBlank(), "Client id is required");
    }
}
