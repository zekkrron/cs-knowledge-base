package lld.parkinglot.v0.model;

import java.time.Duration;
import java.time.Instant;

/** What unpark returns: the ticket (so the driver can print the vehicle), exit time and fee in paise. */
public record Receipt(Ticket ticket, Instant exitTime, long feePaise) {

    public Duration duration() {
        return Duration.between(ticket.entryTime(), exitTime);
    }

    public String feeRupees() {
        return String.format("Rs %d.%02d", feePaise / 100, feePaise % 100);
    }
}
