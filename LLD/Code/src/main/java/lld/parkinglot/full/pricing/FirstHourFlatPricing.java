package lld.parkinglot.full.pricing;

import java.time.Instant;

import lld.parkinglot.full.model.Ticket;

/** E1: common Indian mall scheme, a flat charge for the first hour, then a rate per started hour. */
public final class FirstHourFlatPricing implements PricingStrategy {

    private final double firstHourRupees;
    private final double perExtraHourRupees;

    public FirstHourFlatPricing(double firstHourRupees, double perExtraHourRupees) {
        this.firstHourRupees = firstHourRupees;
        this.perExtraHourRupees = perExtraHourRupees;
    }

    @Override
    public double feeRupees(Ticket ticket, Instant exitTime) {
        long hours = PricingStrategy.billableHours(ticket.entryTime(), exitTime);
        return firstHourRupees + (hours - 1) * perExtraHourRupees;
    }
}
