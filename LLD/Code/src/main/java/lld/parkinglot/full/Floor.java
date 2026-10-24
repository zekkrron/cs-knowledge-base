package lld.parkinglot.full;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public final class Floor {

    private final int number;
    private final List<Slot> slots;

    public Floor(int number, int slotCount, FloorLayout layout) {
        ValidationException.require(slotCount > 0, "Slots per floor must be positive");
        this.number = number;
        List<Slot> built = new ArrayList<>(slotCount);
        for (int s = 1; s <= slotCount; s++) {
            built.add(new Slot(number, s, layout.typeOf(s)));
        }
        this.slots = Collections.unmodifiableList(built);
    }

    public int number() {
        return number;
    }

    public List<Slot> slots() {
        return slots;
    }

    public Slot slot(int slotNumber) {
        ValidationException.require(slotNumber >= 1 && slotNumber <= slots.size(), "No slot " + slotNumber);
        return slots.get(slotNumber - 1);
    }

    public long freeCount(SlotType type) {
        return slots.stream().filter(s -> s.type() == type && s.isFree()).count();
    }
}
