package lld.ratelimiter.full;

import java.util.function.Function;


/** E2: what a rule counts per. Each scope turns a request into the key of its counter. */
public enum Scope {
    CLIENT(Request::clientId),
    ENDPOINT(Request::endpoint),
    CLIENT_ENDPOINT(r -> r.clientId() + "@" + r.endpoint()),
    GLOBAL(r -> "*");

    private final Function<Request, String> keyFn;

    Scope(Function<Request, String> keyFn) {
        this.keyFn = keyFn;
    }

    String keyFor(Request request) {
        return keyFn.apply(request);
    }

    public static Scope parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown scope: " + raw);
        }
    }
}
