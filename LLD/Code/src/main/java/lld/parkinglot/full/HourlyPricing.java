package lld.parkinglot.full;

import java.time.Instant;


/** Flat rate per started hour. */
public final class HourlyPricing implements PricingStrategy {

    private final long ratePerHourPaise;

    public HourlyPricing(long ratePerHourPaise) {
        ValidationException.require(ratePerHourPaise >= 0, "Rate cannot be negative");
        this.ratePerHourPaise = ratePerHourPaise;
    }

    @Override
    public long feePaise(Ticket ticket, Instant exitTime) {
        return PricingStrategy.billableHours(ticket.entryTime(), exitTime) * ratePerHourPaise;
    }
}
