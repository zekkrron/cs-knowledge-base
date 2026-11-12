package lld.parkinglot.v0.model;

import java.time.Duration;
import java.time.Instant;

/** What unpark returns: the ticket (so the driver can print the vehicle), exit time and fee in rupees. */
public record Receipt(Ticket ticket, Instant exitTime, double feeRupees) {

    public Duration duration() {
        return Duration.between(ticket.entryTime(), exitTime);
    }

    public String formattedFee() {
        return String.format("Rs %.2f", feeRupees);
    }
}
