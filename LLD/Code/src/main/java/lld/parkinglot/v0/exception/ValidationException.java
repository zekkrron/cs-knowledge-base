package lld.parkinglot.v0.exception;

/** The input itself is wrong: blank id, non-positive number, unknown type. */
public class ValidationException extends DomainException {

    public ValidationException(String message) {
        super(message);
    }

    /** Throws if {@code condition} is false. Keeps guard clauses to one line. */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new ValidationException(message);
        }
    }
}