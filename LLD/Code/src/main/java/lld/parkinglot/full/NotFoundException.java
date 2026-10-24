package lld.parkinglot.full;

/** The input is well-formed but refers to something that doesn't exist (unknown ticket id). */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}
