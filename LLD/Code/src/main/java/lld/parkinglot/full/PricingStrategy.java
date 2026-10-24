package lld.parkinglot.full;

import java.time.Duration;
import java.time.Instant;

/** E1: fee rule. One implementation per pricing scheme, chosen per vehicle type via a registry. */
public interface PricingStrategy {

    long feePaise(Ticket ticket, Instant exitTime);

    /** Started hours, minimum 1: 0 min -> 1 h, 61 min -> 2 h. */
    static long billableHours(Instant entry, Instant exit) {
        long minutes = Math.max(0, Duration.between(entry, exit).toMinutes());
        return Math.max(1, (minutes + 59) / 60);
    }
}
