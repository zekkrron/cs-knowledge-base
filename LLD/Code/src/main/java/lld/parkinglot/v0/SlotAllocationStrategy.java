package lld.parkinglot.v0;

import java.util.Optional;

/** Picks a slot for a vehicle. The prompt's rule is one implementation; the interviewer's next rule is another. */
public interface SlotAllocationStrategy {

    Optional<Slot> choose(ParkingLot lot, Vehicle vehicle);
}
