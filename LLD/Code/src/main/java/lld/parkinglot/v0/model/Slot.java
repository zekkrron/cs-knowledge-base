package lld.parkinglot.v0.model;

import java.util.Optional;

import lld.parkinglot.v0.exception.ConflictException;

/** One physical slot. Owns the rule "holds at most one vehicle, only of its own type". */
public final class Slot {

    private final int floor;
    private final int number;
    private final VehicleType type;
    private Vehicle vehicle;

    public Slot(int floor, int number, VehicleType type) {
        this.floor = floor;
        this.number = number;
        this.type = type;
    }

    public boolean isFree() {
        return vehicle == null;
    }

    public boolean accepts(Vehicle candidate) {
        return isFree() && candidate.type() == type;
    }

    public void park(Vehicle candidate) {
        if (!accepts(candidate)) {
            throw new ConflictException("Slot " + floor + "-" + number + " cannot take " + candidate.type());
        }
        vehicle = candidate;
    }

    public Vehicle vacate() {
        Vehicle leaving = vehicle;
        vehicle = null;
        return leaving;
    }

    public Optional<Vehicle> vehicle() {
        return Optional.ofNullable(vehicle);
    }

    public int floor() {
        return floor;
    }

    public int number() {
        return number;
    }

    public VehicleType type() {
        return type;
    }
}
