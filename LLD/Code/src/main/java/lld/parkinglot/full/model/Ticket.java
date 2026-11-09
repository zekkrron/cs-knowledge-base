package lld.parkinglot.full.model;

import java.time.Instant;

/**
 * E2 makes the id unique per issue ({@code PR1234_1_4_17}): V0's {@code PR1234_1_4} is reissued
 * every time slot 1-4 is reused, and with bike sharing (E3) several vehicles share one slot.
 */
public record Ticket(String id, Vehicle vehicle, int floor, int slot, SlotType slotType, Instant entryTime) {
}
