package lld.ratelimiter.full;


/**
 * What the limiter sees about one call. {@code cost} (E3) lets one call spend more than one
 * permit: a bulk API, or an LLM call priced in tokens.
 */
public record Request(String clientId, String endpoint, long cost) {

    public Request {
        ValidationException.require(clientId != null && !clientId.isBlank(), "Client id is required");
        ValidationException.require(endpoint != null && !endpoint.isBlank(), "Endpoint is required");
        ValidationException.require(cost >= 1, "Cost must be at least 1");
    }

    public static Request of(String clientId, String endpoint) {
        return new Request(clientId, endpoint, 1);
    }
}
