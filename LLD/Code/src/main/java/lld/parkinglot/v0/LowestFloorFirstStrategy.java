package lld.parkinglot.v0;

import java.util.Optional;

/** The prompt's rule: lowest floor first, then lowest slot number on that floor. */
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
