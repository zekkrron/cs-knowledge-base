package lld.parkinglot.v0.pricing;

import java.time.Instant;

import lld.parkinglot.v0.model.Ticket;

/** One rate for every started hour. Money in paise. */
public final class HourlyPricing implements PricingStrategy {

    private final long perHourPaise;

    public HourlyPricing(long perHourPaise) {
        this.perHourPaise = perHourPaise;
    }

    @Override
    public long feePaise(Ticket ticket, Instant exitTime) {
        return PricingStrategy.billableHours(ticket.entryTime(), exitTime) * perHourPaise;
    }
}
