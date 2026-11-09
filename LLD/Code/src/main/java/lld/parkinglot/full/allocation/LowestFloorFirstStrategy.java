package lld.parkinglot.full.allocation;

import java.util.Comparator;
import java.util.List;

import lld.parkinglot.full.model.ParkingLot;
import lld.parkinglot.full.model.Slot;
import lld.parkinglot.full.model.Vehicle;
import lld.parkinglot.full.rules.CompatibilityRules;

/**
 * The prompt's rule, extended for E3: best-fitting slot type first, then slots already shared
 * by bikes (pack them, keep whole car slots free), then lowest floor, then lowest slot number.
 */
public final class LowestFloorFirstStrategy implements SlotAllocationStrategy {

    @Override
    public List<Slot> candidates(ParkingLot lot, Vehicle vehicle, CompatibilityRules rules) {
        return lot.floors().stream()
                .flatMap(f -> f.slots().stream())
                .filter(s -> rules.rank(vehicle.type(), s.type()) >= 0)
                .filter(s -> s.canTake(vehicle, rules.capacity(s.type(), vehicle.type())))
                .sorted(Comparator
                        .comparingInt((Slot s) -> rules.rank(vehicle.type(), s.type()))
                        .thenComparing(Slot::isEmpty)
                        .thenComparingInt(Slot::floor)
                        .thenComparingInt(Slot::number))
                .toList();
    }
}
