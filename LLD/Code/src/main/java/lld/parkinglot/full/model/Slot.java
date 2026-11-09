package lld.parkinglot.full.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A slot that can hold several vehicles of one type (E3: up to N bikes in a car slot).
 *
 * <p>E2: occupancy is an immutable list inside an {@link AtomicReference}. Claiming and leaving
 * are compare-and-set loops, so gates racing for the same slot can't over-fill it and nobody
 * holds a lock.
 */
public final class Slot {

    private final int floor;
    private final int number;
    private final SlotType type;
    private final AtomicReference<List<Vehicle>> occupants = new AtomicReference<>(List.of());

    public Slot(int floor, int number, SlotType type) {
        this.floor = floor;
        this.number = number;
        this.type = type;
    }

    /** Read-only check used for ranking candidates; the real decision is {@link #tryOccupy}. */
    public boolean canTake(Vehicle vehicle, int capacity) {
        return fits(occupants.get(), vehicle, capacity);
    }

    /** True if this caller added the vehicle. {@code capacity} comes from the compatibility rules. */
    public boolean tryOccupy(Vehicle vehicle, int capacity) {
        while (true) {
            List<Vehicle> current = occupants.get();
            if (!fits(current, vehicle, capacity)) {
                return false;
            }
            List<Vehicle> next = new ArrayList<>(current);
            next.add(vehicle);
            if (occupants.compareAndSet(current, List.copyOf(next))) {
                return true;
            }
            // another gate changed this slot between get and set: re-check and retry
        }
    }

    /** Removes the vehicle if present; false if it wasn't here (double unpark). */
    public boolean vacate(Vehicle vehicle) {
        while (true) {
            List<Vehicle> current = occupants.get();
            if (!current.contains(vehicle)) {
                return false;
            }
            List<Vehicle> next = new ArrayList<>(current);
            next.remove(vehicle);
            if (occupants.compareAndSet(current, List.copyOf(next))) {
                return true;
            }
        }
    }

    private static boolean fits(List<Vehicle> current, Vehicle vehicle, int capacity) {
        boolean sameKind = current.isEmpty() || current.get(0).type() == vehicle.type();
        return sameKind && current.size() < capacity;
    }

    public boolean isEmpty() {
        return occupants.get().isEmpty();
    }

    public List<Vehicle> occupants() {
        return occupants.get();
    }

    public int floor() {
        return floor;
    }

    public int number() {
        return number;
    }

    public SlotType type() {
        return type;
    }
}
