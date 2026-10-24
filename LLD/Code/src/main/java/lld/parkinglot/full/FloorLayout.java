package lld.parkinglot.full;

import java.util.List;


/** E2: which slot type sits at each position on a floor. Floors no longer hard-code the layout. */
@FunctionalInterface
public interface FloorLayout {

    SlotType typeOf(int slotNumber);

    /** The prompt's layout: slot 1 truck, slots 2-3 bike, rest car. */
    static FloorLayout standard() {
        return n -> n == 1 ? SlotType.TRUCK : n <= 3 ? SlotType.BIKE : SlotType.CAR;
    }

    /** Explicit layout, e.g. a floor with EV chargers in slots 4 and 5. */
    static FloorLayout of(List<SlotType> types) {
        return n -> {
            ValidationException.require(n >= 1 && n <= types.size(), "No slot " + n + " in layout");
            return types.get(n - 1);
        };
    }
}
