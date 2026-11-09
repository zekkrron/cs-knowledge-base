package lld.parkinglot.v0.model;

import lld.parkinglot.v0.exception.ValidationException;

/** The prompt's vehicle types. A slot accepts exactly one of these in V0. */
public enum VehicleType {
    TRUCK, BIKE, CAR;

    public static VehicleType parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown vehicle type: " + raw);
        }
    }
}
