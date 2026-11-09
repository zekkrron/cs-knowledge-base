package lld.parkinglot.full.model;

import lld.parkinglot.full.exception.ValidationException;

/**
 * E3: slot size is no longer the same thing as vehicle type, because a bike may use a car slot
 * and a car may use a truck slot. Ordered smallest to largest.
 */
public enum SlotType {
    BIKE, CAR, TRUCK;

    public static SlotType parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown slot type: " + raw);
        }
    }
}
