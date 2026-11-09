package lld.parkinglot.full.exception;

/** The request is valid but the current state refuses it (lot full, already parked, limit exceeded). */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}