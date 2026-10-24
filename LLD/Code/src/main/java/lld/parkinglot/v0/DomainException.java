package lld.parkinglot.v0;

/**
 * Base of every exception this solution throws on purpose.
 *
 * <p>Unchecked, because callers usually can't recover in the middle of a call chain; the
 * CLI driver catches {@code DomainException} once and prints its message. Anything that is
 * NOT a DomainException is a bug and should crash loudly.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
