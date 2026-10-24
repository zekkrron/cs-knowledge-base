package lld.parkinglot.v0;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A floor and its slots. The default layout from the prompt: slot 1 is for a truck,
 * slots 2 and 3 for bikes, the rest for cars.
 */
public final class Floor {

    private final int number;
    private final List<Slot> slots;

    public Floor(int number, int slotCount) {
        this.number = number;
        List<Slot> built = new ArrayList<>(slotCount);
        for (int s = 1; s <= slotCount; s++) {
            built.add(new Slot(number, s, defaultTypeFor(s)));
        }
        this.slots = Collections.unmodifiableList(built);
    }

    private static VehicleType defaultTypeFor(int slotNumber) {
        if (slotNumber == 1) {
            return VehicleType.TRUCK;
        }
        if (slotNumber <= 3) {
            return VehicleType.BIKE;
        }
        return VehicleType.CAR;
    }

    public int number() {
        return number;
    }

    public List<Slot> slots() {
        return slots;
    }

    public Slot slot(int slotNumber) {
        return slots.get(slotNumber - 1);
    }
}
