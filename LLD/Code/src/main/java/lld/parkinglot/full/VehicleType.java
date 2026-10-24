package lld.parkinglot.full;


/** E2 adds EV_CAR. Adding a type touches this enum and the compatibility table, nothing else. */
public enum VehicleType {
    TRUCK, BIKE, CAR, EV_CAR;

    public static VehicleType parse(String raw) {
        try {
            return VehicleType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown vehicle type: " + raw);
        }
    }
}
