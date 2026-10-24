package lld.parkinglot.v0;

/** Ticket id format from the prompt: {@code <lotId>_<floor>_<slot>}, e.g. {@code PR1234_2_5}. */
public record Ticket(String id, Vehicle vehicle, int floor, int slot) {

    public static Ticket issue(String lotId, Vehicle vehicle, Slot slot) {
        return new Ticket(lotId + "_" + slot.floor() + "_" + slot.number(), vehicle, slot.floor(), slot.number());
    }
}
