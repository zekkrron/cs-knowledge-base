package lld.ratelimiter.v0;

/** The contract callers depend on. V0 answers yes/no; the full version returns a decision object. */
public interface RateLimiter {

    boolean tryAcquire(String clientId);
}
