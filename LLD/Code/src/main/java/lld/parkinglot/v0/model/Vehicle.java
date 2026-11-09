package lld.parkinglot.v0.model;

import lld.parkinglot.v0.exception.ValidationException;

/** Value object: a vehicle is identified by its registration number. */
public record Vehicle(VehicleType type, String registrationNumber, String color) {

    public Vehicle {
        ValidationException.require(type != null, "Vehicle type is required");
        ValidationException.require(registrationNumber != null && !registrationNumber.isBlank(),
                "Registration number is required");
        ValidationException.require(color != null && !color.isBlank(), "Color is required");
    }
}
