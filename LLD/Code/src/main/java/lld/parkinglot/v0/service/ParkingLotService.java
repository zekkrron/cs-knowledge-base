package lld.parkinglot.v0.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lld.parkinglot.v0.allocation.SlotAllocationStrategy;
import lld.parkinglot.v0.exception.ConflictException;
import lld.parkinglot.v0.exception.NotFoundException;
import lld.parkinglot.v0.model.Floor;
import lld.parkinglot.v0.model.ParkingLot;
import lld.parkinglot.v0.model.Receipt;
import lld.parkinglot.v0.model.Slot;
import lld.parkinglot.v0.model.Ticket;
import lld.parkinglot.v0.model.Vehicle;
import lld.parkinglot.v0.model.VehicleType;
import lld.parkinglot.v0.pricing.PricingStrategy;

/**
 * Orchestrator: find slot, park, issue ticket; unpark and bill; availability queries.
 * Never reads input or prints: drivers call it and format the results.
 *
 * <p>V0 is single-threaded on purpose (one CLI user); concurrent gates are extension E2.
 */
public final class ParkingLotService {

    private final ParkingLot lot;
    private final SlotAllocationStrategy allocation;
    private final PricingStrategy pricing;
    private final Clock clock;
    private final Map<String, Ticket> activeTickets = new HashMap<>();

    public ParkingLotService(ParkingLot lot, SlotAllocationStrategy allocation, PricingStrategy pricing, Clock clock) {
        this.lot = lot;
        this.allocation = allocation;
        this.pricing = pricing;
        this.clock = clock;
    }

    public Ticket park(Vehicle vehicle) {
        Slot slot = allocation.choose(lot, vehicle)
                .orElseThrow(() -> new ConflictException("Parking Lot Full"));
        slot.park(vehicle);
        Ticket ticket = Ticket.issue(lot.id(), vehicle, slot, clock.instant());
        activeTickets.put(ticket.id(), ticket);
        return ticket;
    }

    public Receipt unpark(String ticketId) {
        Ticket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new NotFoundException("Invalid Ticket");
        }
        lot.floor(ticket.floor()).slot(ticket.slot()).vacate();
        Instant exit = clock.instant();
        return new Receipt(ticket, exit, pricing.feeRupees(ticket, exit));
    }

    public Map<Integer, List<Integer>> freeSlots(VehicleType type) {
        return slotsMatching(type, true);
    }

    public Map<Integer, List<Integer>> occupiedSlots(VehicleType type) {
        return slotsMatching(type, false);
    }

    private Map<Integer, List<Integer>> slotsMatching(VehicleType type, boolean free) {
        Map<Integer, List<Integer>> byFloor = new LinkedHashMap<>();
        for (Floor floor : lot.floors()) {
            byFloor.put(floor.number(), floor.slots().stream()
                    .filter(s -> s.type() == type && s.isFree() == free)
                    .map(Slot::number)
                    .toList());
        }
        return byFloor;
    }
}
