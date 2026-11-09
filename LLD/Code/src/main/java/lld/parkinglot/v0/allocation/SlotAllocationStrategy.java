package lld.parkinglot.v0.allocation;

import java.util.Optional;

import lld.parkinglot.v0.model.ParkingLot;
import lld.parkinglot.v0.model.Slot;
import lld.parkinglot.v0.model.Vehicle;

/** Which free slot a vehicle gets. An interface from V0 because the rule is the first thing reviewers change. */
public interface SlotAllocationStrategy {

    Optional<Slot> choose(ParkingLot lot, Vehicle vehicle);
}
