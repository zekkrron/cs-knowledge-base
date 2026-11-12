package lld.parkinglot.full.pricing;

import java.time.Instant;

import lld.parkinglot.full.model.Ticket;

/** One rate for every started hour, in rupees. */
public final class HourlyPricing implements PricingStrategy {

    private final double perHourRupees;

    public HourlyPricing(double perHourRupees) {
        this.perHourRupees = perHourRupees;
    }

    @Override
    public double feeRupees(Ticket ticket, Instant exitTime) {
        return PricingStrategy.billableHours(ticket.entryTime(), exitTime) * perHourRupees;
    }
}
