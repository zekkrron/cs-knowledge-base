package lld.parkinglot.full;

import java.time.Instant;

/**
 * E1 adds {@code entryTime} for pricing. E4 makes the id unique per issue
 * ({@code PR1234_1_4_17}): in V0 the id {@code PR1234_1_4} is reissued every time slot
 * 1-4 is reused, so an old paper ticket could unpark the next car in that slot.
 */
public record Ticket(String id, Vehicle vehicle, int floor, int slot, SlotType slotType, Instant entryTime) {
}
