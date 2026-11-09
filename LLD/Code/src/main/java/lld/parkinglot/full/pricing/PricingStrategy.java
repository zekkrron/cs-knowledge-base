package lld.parkinglot.full.pricing;

import java.time.Duration;
import java.time.Instant;

import lld.parkinglot.full.model.Ticket;

/** Fee rule. E1: one strategy per vehicle type, chosen from a map in the service. */
public interface PricingStrategy {

    long feePaise(Ticket ticket, Instant exitTime);

    /** Started hours, minimum 1: 0 min -> 1 h, 61 min -> 2 h. */
    static long billableHours(Instant entry, Instant exit) {
        long minutes = Math.max(0, Duration.between(entry, exit).toMinutes());
        return Math.max(1, (minutes + 59) / 60);
    }
}
