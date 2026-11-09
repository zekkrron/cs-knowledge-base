package lld.ratelimiter.full.exception;

/**
 * Base of every exception this solution throws on purpose. Unchecked: the driver catches it
 * once and shows the message. Anything that is not a DomainException is a bug.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}