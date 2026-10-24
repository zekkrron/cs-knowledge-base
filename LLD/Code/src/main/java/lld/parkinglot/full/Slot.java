package lld.parkinglot.full;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * E4: occupancy is an {@link AtomicReference}. Claiming a slot is one compare-and-set,
 * so two gates racing for the same slot can't both win, and no lock is held.
 */
public final class Slot {

    private final int floor;
    private final int number;
    private final SlotType type;
    private final AtomicReference<Vehicle> occupant = new AtomicReference<>();

    public Slot(int floor, int number, SlotType type) {
        this.floor = floor;
        this.number = number;
        this.type = type;
    }

    /** Returns true if this caller won the slot. */
    public boolean tryOccupy(Vehicle vehicle) {
        return occupant.compareAndSet(null, vehicle);
    }

    /** Frees the slot only if it still holds {@code expected}; protects against double unpark. */
    public boolean vacate(Vehicle expected) {
        return occupant.compareAndSet(expected, null);
    }

    public boolean isFree() {
        return occupant.get() == null;
    }

    public Optional<Vehicle> vehicle() {
        return Optional.ofNullable(occupant.get());
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
