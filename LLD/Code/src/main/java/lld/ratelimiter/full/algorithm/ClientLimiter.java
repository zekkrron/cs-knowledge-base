package lld.ratelimiter.full.algorithm;

import lld.ratelimiter.full.model.Decision;

/**
 * Counting state for one client. E4: a request may cost more than 1 (LLM tokens, batch size).
 *
 * <p>Not thread-safe by itself: the service only calls it inside
 * {@code ConcurrentHashMap.compute} for that client's key, which serialises calls per client.
 * Time is epoch milliseconds.
 */
public interface ClientLimiter {

    Decision tryAcquire(long nowMillis, int cost);
}
