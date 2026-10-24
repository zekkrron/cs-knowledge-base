package lld.parkinglot.full;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public final class ParkingLot {

    private final String id;
    private final List<Floor> floors;

    /** Same layout on every floor (the prompt's case). */
    public ParkingLot(String id, int floorCount, int slotsPerFloor, FloorLayout layout) {
        this(id, buildUniform(floorCount, slotsPerFloor, layout));
    }

    /** E2: floors with different sizes and layouts. */
    public ParkingLot(String id, List<Floor> floors) {
        ValidationException.require(id != null && !id.isBlank(), "Parking lot id is required");
        ValidationException.require(floors != null && !floors.isEmpty(), "At least one floor is required");
        this.id = id;
        this.floors = Collections.unmodifiableList(new ArrayList<>(floors));
    }

    private static List<Floor> buildUniform(int floorCount, int slotsPerFloor, FloorLayout layout) {
        ValidationException.require(floorCount > 0, "Floor count must be positive");
        List<Floor> built = new ArrayList<>(floorCount);
        for (int f = 1; f <= floorCount; f++) {
            built.add(new Floor(f, slotsPerFloor, layout));
        }
        return built;
    }

    public String id() {
        return id;
    }

    public List<Floor> floors() {
        return floors;
    }

    public Floor floor(int number) {
        ValidationException.require(number >= 1 && number <= floors.size(), "No floor " + number);
        return floors.get(number - 1);
    }
}
