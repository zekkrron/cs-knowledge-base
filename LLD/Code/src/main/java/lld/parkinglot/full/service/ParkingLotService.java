package lld.parkinglot.full.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import lld.parkinglot.full.allocation.SlotAllocationStrategy;
import lld.parkinglot.full.exception.ConflictException;
import lld.parkinglot.full.exception.NotFoundException;
import lld.parkinglot.full.exception.ValidationException;
import lld.parkinglot.full.model.Floor;
import lld.parkinglot.full.model.ParkingLot;
import lld.parkinglot.full.model.Receipt;
import lld.parkinglot.full.model.Slot;
import lld.parkinglot.full.model.SlotType;
import lld.parkinglot.full.model.Ticket;
import lld.parkinglot.full.model.Vehicle;
import lld.parkinglot.full.model.VehicleType;
import lld.parkinglot.full.pricing.PricingStrategy;
import lld.parkinglot.full.rules.CompatibilityRules;

/**
 * Orchestrator: park, unpark and bill, availability and lookup queries. Never reads input or
 * prints.
 *
 * <ul>
 *   <li>E1: pricing is a strategy per vehicle type.</li>
 *   <li>E2: safe under concurrent gates. Slots are claimed with CAS ({@link Slot#tryOccupy}),
 *       tickets live in concurrent maps, ids carry a sequence number, and a ticket is removed
 *       atomically so two exits on one ticket bill once.</li>
 *   <li>E3: which slots a vehicle may use and how many share a slot come from
 *       {@link CompatibilityRules}.</li>
 *   <li>E4: lookups by registration (lost ticket) and colour; a registration can't be parked twice.</li>
 * </ul>
 */
public final class ParkingLotService {

    private final ParkingLot lot;
    private final CompatibilityRules rules;
    private final SlotAllocationStrategy allocation;
    private final Map<VehicleType, PricingStrategy> pricing;
    private final Clock clock;

    private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();
    private final Map<String, Ticket> ticketByRegistration = new ConcurrentHashMap<>();
    private final Set<String> parkedRegistrations = ConcurrentHashMap.newKeySet();
    private final AtomicLong sequence = new AtomicLong();

    public ParkingLotService(ParkingLot lot, CompatibilityRules rules, SlotAllocationStrategy allocation,
            Map<VehicleType, PricingStrategy> pricing, Clock clock) {
        this.lot = lot;
        this.rules = rules;
        this.allocation = allocation;
        this.pricing = Map.copyOf(pricing);
        this.clock = clock;
    }

    public Ticket park(Vehicle vehicle) {
        ValidationException.require(pricing.containsKey(vehicle.type()), "No pricing for " + vehicle.type());
        String reg = vehicle.registrationNumber();
        // E4 + E2: the set add is atomic, so two gates entering the same plate can't both pass
        if (!parkedRegistrations.add(reg)) {
            throw new ConflictException("Vehicle already parked");
        }
        try {
            for (Slot slot : allocation.candidates(lot, vehicle, rules)) {
                if (slot.tryOccupy(vehicle, rules.capacity(slot.type(), vehicle.type()))) {
                    Ticket ticket = new Ticket(
                            lot.id() + "_" + slot.floor() + "_" + slot.number() + "_" + sequence.incrementAndGet(),
                            vehicle, slot.floor(), slot.number(), slot.type(), clock.instant());
                    ticketByRegistration.put(reg, ticket);
                    activeTickets.put(ticket.id(), ticket);
                    return ticket;
                }
                // lost the race for this slot: try the next candidate
            }
        } catch (RuntimeException e) {
            parkedRegistrations.remove(reg);
            throw e;
        }
        parkedRegistrations.remove(reg);
        throw new ConflictException("Parking Lot Full");
    }

    public Receipt unpark(String ticketId) {
        Ticket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new NotFoundException("Invalid Ticket");
        }
        lot.floor(ticket.floor()).slot(ticket.slot()).vacate(ticket.vehicle());
        String reg = ticket.vehicle().registrationNumber();
        ticketByRegistration.remove(reg);
        parkedRegistrations.remove(reg); // last, so a re-entry can't race the cleanup above
        Instant exit = clock.instant();
        return new Receipt(ticket, exit, pricing.get(ticket.vehicle().type()).feePaise(ticket, exit));
    }

    /** E4: lost ticket, find the car by its plate. */
    public Ticket ticketForRegistration(String registrationNumber) {
        Ticket ticket = ticketByRegistration.get(registrationNumber);
        if (ticket == null) {
            throw new NotFoundException("Vehicle not found");
        }
        return ticket;
    }

    /** E4: active tickets for vehicles of this colour, ordered by floor then slot. */
    public List<Ticket> ticketsWithColor(String color) {
        return activeTickets.values().stream()
                .filter(t -> t.vehicle().color().equalsIgnoreCase(color))
                .sorted(Comparator.comparingInt(Ticket::floor).thenComparingInt(Ticket::slot)
                        .thenComparing(Ticket::id))
                .toList();
    }

    /** E3: busy-hours switch, several bikes per car slot. */
    public void setBikeSharing(boolean enabled) {
        rules.setBikeSharing(enabled);
    }

    /** Slots of this type with nobody in them. */
    public Map<Integer, List<Integer>> freeSlots(SlotType type) {
        return slotsMatching(type, true);
    }

    /** Slots of this type holding at least one vehicle. */
    public Map<Integer, List<Integer>> occupiedSlots(SlotType type) {
        return slotsMatching(type, false);
    }

    public int activeTicketCount() {
        return activeTickets.size();
    }

    private Map<Integer, List<Integer>> slotsMatching(SlotType type, boolean free) {
        Map<Integer, List<Integer>> byFloor = new LinkedHashMap<>();
        for (Floor floor : lot.floors()) {
            byFloor.put(floor.number(), floor.slots().stream()
                    .filter(s -> s.type() == type && s.isEmpty() == free)
                    .map(Slot::number)
                    .toList());
        }
        return byFloor;
    }
}
