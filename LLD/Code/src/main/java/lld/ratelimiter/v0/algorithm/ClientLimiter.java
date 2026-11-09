package lld.ratelimiter.v0.algorithm;

import java.time.Instant;

/**
 * Counting state for one client. An interface because "now use a different algorithm" is the
 * most reported follow-up (E1).
 *
 * <p>Not thread-safe by itself: the service only calls it inside
 * {@code ConcurrentHashMap.compute} for that client's key, which serialises calls per client.
 */
public interface ClientLimiter {

    boolean tryAcquire(Instant now);
}
