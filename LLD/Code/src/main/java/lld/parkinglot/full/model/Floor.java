package lld.parkinglot.full.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A floor and its slots. The prompt's layout: slot 1 truck, slots 2-3 bike, the rest car. */
public final class Floor {

    private final int number;
    private final List<Slot> slots;

    public Floor(int number, int slotCount) {
        this.number = number;
        List<Slot> built = new ArrayList<>(slotCount);
        for (int s = 1; s <= slotCount; s++) {
            built.add(new Slot(number, s, typeFor(s)));
        }
        this.slots = Collections.unmodifiableList(built);
    }

    private static SlotType typeFor(int slotNumber) {
        if (slotNumber == 1) {
            return SlotType.TRUCK;
        }
        return slotNumber <= 3 ? SlotType.BIKE : SlotType.CAR;
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
