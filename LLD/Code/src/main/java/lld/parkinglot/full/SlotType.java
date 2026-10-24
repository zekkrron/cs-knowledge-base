package lld.parkinglot.full;


/**
 * E2: slot type is no longer the same thing as vehicle type. An EV charging slot is a
 * slot type; which vehicles may use it is a rule in {@link CompatibilityRules}.
 */
public enum SlotType {
    TRUCK, BIKE, CAR, EV_CHARGING;

    public static SlotType parse(String raw) {
        try {
            return SlotType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown slot type: " + raw);
        }
    }
}
