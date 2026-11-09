package lld.parkinglot.full.exception;

/** Well-formed input that refers to something that doesn't exist (unknown ticket id). */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}