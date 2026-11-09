package lld.parkinglot.v0.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lld.parkinglot.v0.exception.ValidationException;

/** The physical structure: floors and slots. Knows nothing about tickets or money. */
public final class ParkingLot {

    private final String id;
    private final List<Floor> floors;

    public ParkingLot(String id, int floorCount, int slotsPerFloor) {
        ValidationException.require(id != null && !id.isBlank(), "Parking lot id is required");
        ValidationException.require(floorCount > 0, "Floor count must be positive");
        ValidationException.require(slotsPerFloor > 0, "Slots per floor must be positive");
        this.id = id;
        List<Floor> built = new ArrayList<>(floorCount);
        for (int f = 1; f <= floorCount; f++) {
            built.add(new Floor(f, slotsPerFloor));
        }
        this.floors = Collections.unmodifiableList(built);
    }

    public String id() {
        return id;
    }

    public List<Floor> floors() {
        return floors;
    }

    public Floor floor(int number) {
        return floors.get(number - 1);
    }
}
