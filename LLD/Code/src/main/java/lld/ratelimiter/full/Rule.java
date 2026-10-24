package lld.ratelimiter.full;


/** E2: one limit that applies to every request, counted per {@link Scope} key. */
public record Rule(String name, Scope scope, LimitPolicy policy) {

    public Rule {
        ValidationException.require(name != null && !name.isBlank(), "Rule name is required");
        ValidationException.require(scope != null, "Scope is required");
        ValidationException.require(policy != null, "Policy is required");
    }
}
