package lld.parkinglot.full;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Full version: V0 + E1 pricing + E2 compatibility/layouts + E3 switchable allocation +
 * E4 safe for many gates at once.
 *
 * <p>Thread safety without a global lock:
 * <ul>
 *   <li>claiming a slot is a CAS on that slot ({@link Slot#tryOccupy});</li>
 *   <li>active tickets live in a {@link ConcurrentHashMap}; unpark uses {@code remove},
 *       so two exit gates presenting the same ticket can't both succeed;</li>
 *   <li>the allocation strategy reference is {@code volatile} so a switch is seen by all gates.</li>
 * </ul>
 */
public final class ParkingLotService {

    private final ParkingLot lot;
    private final CompatibilityRules rules;
    private final Map<VehicleType, PricingStrategy> pricing;
    private final Clock clock;
    private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();
    private final AtomicLong ticketSequence = new AtomicLong();
    private volatile SlotAllocationStrategy allocation;

    public ParkingLotService(ParkingLot lot,
                             CompatibilityRules rules,
                             SlotAllocationStrategy allocation,
                             Map<VehicleType, PricingStrategy> pricing,
                             Clock clock) {
        this.lot = lot;
        this.rules = rules;
        this.allocation = allocation;
        this.pricing = new EnumMap<>(pricing);
        this.clock = clock;
    }

    public Ticket park(Vehicle vehicle) {
        pricingFor(vehicle.type()); // fail fast: don't park a vehicle we can't bill
        for (Slot slot : allocation.candidates(lot, vehicle, rules)) {
            if (slot.tryOccupy(vehicle)) {
                Ticket ticket = new Ticket(
                        lot.id() + "_" + slot.floor() + "_" + slot.number() + "_" + ticketSequence.incrementAndGet(),
                        vehicle, slot.floor(), slot.number(), slot.type(), clock.instant());
                activeTickets.put(ticket.id(), ticket);
                return ticket;
            }
            // Lost the race for this slot to another gate: try the next candidate.
        }
        throw new ConflictException("Parking Lot Full");
    }

    public Receipt unpark(String ticketId) {
        Ticket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new NotFoundException("Invalid Ticket");
        }
        Instant exit = clock.instant();
        long fee = pricingFor(ticket.vehicle().type()).feePaise(ticket, exit);
        lot.floor(ticket.floor()).slot(ticket.slot()).vacate(ticket.vehicle());
        return new Receipt(ticket, exit, fee);
    }

    private PricingStrategy pricingFor(VehicleType type) {
        PricingStrategy strategy = pricing.get(type);
        if (strategy == null) {
            throw new ValidationException("No pricing configured for " + type);
        }
        return strategy;
    }

    public void switchAllocation(SlotAllocationStrategy next) {
        this.allocation = next;
    }

    public String allocationName() {
        return allocation.name();
    }

    public int activeTicketCount() {
        return activeTickets.size();
    }

    public Map<Integer, List<Integer>> freeSlots(SlotType type) {
        return slotsMatching(type, true);
    }

    public Map<Integer, List<Integer>> occupiedSlots(SlotType type) {
        return slotsMatching(type, false);
    }

    private Map<Integer, List<Integer>> slotsMatching(SlotType type, boolean free) {
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
