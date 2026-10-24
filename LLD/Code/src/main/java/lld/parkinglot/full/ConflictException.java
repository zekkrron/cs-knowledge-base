package lld.parkinglot.full;

/** The request is valid but the current state refuses it: lot full, limit exceeded, no lot yet. */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}
