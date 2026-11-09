package lld.parkinglot.full.model;

import lld.parkinglot.full.exception.ValidationException;

/** Vehicle types. From E3, which slot types each may use is a rule in {@code CompatibilityRules}. */
public enum VehicleType {
    BIKE, CAR, TRUCK;

    public static VehicleType parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown vehicle type: " + raw);
        }
    }
}
