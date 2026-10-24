package lld.parkinglot.full;


public record Vehicle(VehicleType type, String registrationNumber, String color) {

    public Vehicle {
        ValidationException.require(type != null, "Vehicle type is required");
        ValidationException.require(registrationNumber != null && !registrationNumber.isBlank(),
                "Registration number is required");
        ValidationException.require(color != null && !color.isBlank(), "Color is required");
    }
}
