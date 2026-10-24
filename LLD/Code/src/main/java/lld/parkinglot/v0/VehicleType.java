package lld.parkinglot.v0;


public enum VehicleType {
    TRUCK, BIKE, CAR;

    public static VehicleType parse(String raw) {
        try {
            return VehicleType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown vehicle type: " + raw);
        }
    }
}
