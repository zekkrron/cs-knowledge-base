package lld.parkinglot.v0.pricing;

import java.time.Duration;
import java.time.Instant;

import lld.parkinglot.v0.model.Ticket;

/** Fee rule. One implementation in V0; an interface because "price it differently" is the top follow-up. */
public interface PricingStrategy {

    long feePaise(Ticket ticket, Instant exitTime);

    /** Started hours, minimum 1: 0 min -> 1 h, 61 min -> 2 h. */
    static long billableHours(Instant entry, Instant exit) {
        long minutes = Math.max(0, Duration.between(entry, exit).toMinutes());
        return Math.max(1, (minutes + 59) / 60);
    }
}
