package lld.parkinglot.full.pricing;

import java.time.Instant;

import lld.parkinglot.full.model.Ticket;

/** E1: common Indian mall scheme, a flat charge for the first hour, then a rate per started hour. */
public final class FirstHourFlatPricing implements PricingStrategy {

    private final long firstHourPaise;
    private final long perExtraHourPaise;

    public FirstHourFlatPricing(long firstHourPaise, long perExtraHourPaise) {
        this.firstHourPaise = firstHourPaise;
        this.perExtraHourPaise = perExtraHourPaise;
    }

    @Override
    public long feePaise(Ticket ticket, Instant exitTime) {
        long hours = PricingStrategy.billableHours(ticket.entryTime(), exitTime);
        return firstHourPaise + (hours - 1) * perExtraHourPaise;
    }
}
