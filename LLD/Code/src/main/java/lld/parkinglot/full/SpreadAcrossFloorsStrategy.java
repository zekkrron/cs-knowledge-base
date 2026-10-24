package lld.parkinglot.full;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * E3: spread load (and ramp traffic) by sending each vehicle to the floor with the most
 * free compatible slots. Added as a new class; nothing else changed.
 */
public final class SpreadAcrossFloorsStrategy implements SlotAllocationStrategy {

    @Override
    public List<Slot> candidates(ParkingLot lot, Vehicle vehicle, CompatibilityRules rules) {
        List<Slot> usable = lot.floors().stream()
                .flatMap(f -> f.slots().stream())
                .filter(Slot::isFree)
                .filter(s -> rules.rank(vehicle.type(), s.type()) >= 0)
                .toList();
        Map<Integer, Long> freePerFloor = usable.stream()
                .collect(Collectors.groupingBy(Slot::floor, Collectors.counting()));
        Function<Slot, Long> floorFreeness = s -> freePerFloor.getOrDefault(s.floor(), 0L);
        return usable.stream()
                .sorted(Comparator
                        .comparingInt((Slot s) -> rules.rank(vehicle.type(), s.type()))
                        .thenComparing(floorFreeness, Comparator.reverseOrder())
                        .thenComparingInt(Slot::floor)
                        .thenComparingInt(Slot::number))
                .toList();
    }

    @Override
    public String name() {
        return "SPREAD";
    }
}
