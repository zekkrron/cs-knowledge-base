package lld.parkinglot.full;

import java.time.Duration;
import java.time.Instant;

/** E1: what the exit gate prints. Money is in paise (long), never floating point. */
public record Receipt(Ticket ticket, Instant exitTime, long feePaise) {

    public Duration duration() {
        return Duration.between(ticket.entryTime(), exitTime);
    }

    public String feeRupees() {
        return String.format("Rs %d.%02d", feePaise / 100, feePaise % 100);
    }
}
