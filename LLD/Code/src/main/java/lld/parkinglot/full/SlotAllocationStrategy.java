package lld.parkinglot.full;

import java.util.List;

/**
 * E3/E4: a strategy returns candidate slots in preference order instead of one slot.
 * The service then tries to claim them one by one with a CAS, so a slot taken by another
 * gate between "choose" and "claim" just means "try the next one".
 */
public interface SlotAllocationStrategy {

    List<Slot> candidates(ParkingLot lot, Vehicle vehicle, CompatibilityRules rules);

    String name();
}
