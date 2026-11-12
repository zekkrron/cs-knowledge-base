package lld.parkinglot.v0.demo;

import java.time.Duration;
import java.time.Instant;

import lld.parkinglot.v0.allocation.LowestFloorFirstStrategy;
import lld.parkinglot.v0.exception.DomainException;
import lld.parkinglot.v0.model.ParkingLot;
import lld.parkinglot.v0.model.Receipt;
import lld.parkinglot.v0.model.Ticket;
import lld.parkinglot.v0.model.Vehicle;
import lld.parkinglot.v0.model.VehicleType;
import lld.parkinglot.v0.pricing.HourlyPricing;
import lld.parkinglot.v0.service.ParkingLotService;

/** Plain driver: builds the service and runs a short scenario by calling its methods. */
public final class Demo {

    public static void main(String[] args) {
        ManualClock clock = new ManualClock(Instant.parse("2026-10-27T10:00:00Z"));
        ParkingLotService service = new ParkingLotService(
                new ParkingLot("PR1234", 2, 6), new LowestFloorFirstStrategy(), new HourlyPricing(30.0), clock);

        Ticket car = service.park(new Vehicle(VehicleType.CAR, "KA-01-DB-1234", "black"));
        System.out.println("Parked car, ticket " + car.id());
        Ticket truck = service.park(new Vehicle(VehicleType.TRUCK, "KA-02-T-0001", "white"));
        System.out.println("Parked truck, ticket " + truck.id());

        clock.advance(Duration.ofMinutes(125));
        Receipt receipt = service.unpark(car.id());
        System.out.println("Unparked " + receipt.ticket().vehicle().registrationNumber()
                + " after " + receipt.duration().toMinutes() + " min, fee " + receipt.formattedFee());

        try {
            service.unpark(car.id());
        } catch (DomainException e) {
            System.out.println("Second unpark: " + e.getMessage());
        }
        System.out.println("Free car slots by floor: " + service.freeSlots(VehicleType.CAR));
    }
}
