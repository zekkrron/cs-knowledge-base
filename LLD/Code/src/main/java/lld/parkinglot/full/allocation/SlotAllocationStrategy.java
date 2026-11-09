package lld.parkinglot.full.allocation;

import java.util.List;

import lld.parkinglot.full.model.ParkingLot;
import lld.parkinglot.full.model.Slot;
import lld.parkinglot.full.model.Vehicle;
import lld.parkinglot.full.rules.CompatibilityRules;

/**
 * E2: returns candidate slots in preference order instead of one slot. The service claims them
 * one by one with a CAS, so a slot taken by another gate between "choose" and "claim" just means
 * "try the next". E3: candidates respect the compatibility rules.
 */
public interface SlotAllocationStrategy {

    List<Slot> candidates(ParkingLot lot, Vehicle vehicle, CompatibilityRules rules);
}
