package lld.parkinglot.full;

import java.util.Comparator;
import java.util.List;

/**
 * The prompt's rule, extended for E2: best slot type first (EV charger before a normal
 * car slot), then lowest floor, then lowest slot number.
 */
public final class LowestFloorFirstStrategy implements SlotAllocationStrategy {

    @Override
    public List<Slot> candidates(ParkingLot lot, Vehicle vehicle, CompatibilityRules rules) {
        return lot.floors().stream()
                .flatMap(f -> f.slots().stream())
                .filter(Slot::isFree)
                .filter(s -> rules.rank(vehicle.type(), s.type()) >= 0)
                .sorted(Comparator
                        .comparingInt((Slot s) -> rules.rank(vehicle.type(), s.type()))
                        .thenComparingInt(Slot::floor)
                        .thenComparingInt(Slot::number))
                .toList();
    }

    @Override
    public String name() {
        return "LOWEST_FLOOR";
    }
}
