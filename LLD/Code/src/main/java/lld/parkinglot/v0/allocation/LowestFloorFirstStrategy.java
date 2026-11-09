package lld.parkinglot.v0.allocation;

import java.util.Optional;

import lld.parkinglot.v0.model.Floor;
import lld.parkinglot.v0.model.ParkingLot;
import lld.parkinglot.v0.model.Slot;
import lld.parkinglot.v0.model.Vehicle;

/** The prompt's rule: lowest floor first, then lowest slot number (nearest to the entry). */
public final class LowestFloorFirstStrategy implements SlotAllocationStrategy {

    @Override
    public Optional<Slot> choose(ParkingLot lot, Vehicle vehicle) {
        for (Floor floor : lot.floors()) {
            for (Slot slot : floor.slots()) {
                if (slot.accepts(vehicle)) {
                    return Optional.of(slot);
                }
            }
        }
        return Optional.empty();
    }
}
