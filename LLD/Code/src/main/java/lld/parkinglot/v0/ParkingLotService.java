package lld.parkinglot.v0;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Orchestrator: owns the workflow (find slot, park, issue ticket, unpark) and the
 * active tickets. Rules about a single slot stay in {@link Slot}.
 *
 * <p>V0 is single-threaded on purpose: the classic prompt is a CLI with one user.
 */
public final class ParkingLotService {

    private final ParkingLot lot;
    private final SlotAllocationStrategy allocation;
    private final Map<String, Ticket> activeTickets = new HashMap<>();

    public ParkingLotService(ParkingLot lot, SlotAllocationStrategy allocation) {
        this.lot = lot;
        this.allocation = allocation;
    }

    public Ticket park(Vehicle vehicle) {
        Slot slot = allocation.choose(lot, vehicle)
                .orElseThrow(() -> new ConflictException("Parking Lot Full"));
        slot.park(vehicle);
        Ticket ticket = Ticket.issue(lot.id(), vehicle, slot);
        activeTickets.put(ticket.id(), ticket);
        return ticket;
    }

    public Vehicle unpark(String ticketId) {
        Ticket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new NotFoundException("Invalid Ticket");
        }
        return lot.floor(ticket.floor()).slot(ticket.slot()).vacate();
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
