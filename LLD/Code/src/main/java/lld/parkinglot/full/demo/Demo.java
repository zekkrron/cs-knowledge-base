package lld.parkinglot.full.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import lld.parkinglot.full.allocation.LowestFloorFirstStrategy;
import lld.parkinglot.full.exception.DomainException;
import lld.parkinglot.full.model.ParkingLot;
import lld.parkinglot.full.model.Receipt;
import lld.parkinglot.full.model.SlotType;
import lld.parkinglot.full.model.Ticket;
import lld.parkinglot.full.model.Vehicle;
import lld.parkinglot.full.model.VehicleType;
import lld.parkinglot.full.pricing.FirstHourFlatPricing;
import lld.parkinglot.full.pricing.HourlyPricing;
import lld.parkinglot.full.rules.CompatibilityRules;
import lld.parkinglot.full.service.ParkingLotService;

/** Plain driver: one short scenario per extension, calling the service directly. */
public final class Demo {

    public static void main(String[] args) {
        ManualClock clock = new ManualClock(Instant.parse("2026-10-27T10:00:00Z"));
        ParkingLotService service = new ParkingLotService(
                new ParkingLot("PR1234", 1, 4), CompatibilityRules.defaults(), new LowestFloorFirstStrategy(),
                Map.of(VehicleType.BIKE, new HourlyPricing(10.0),
                        VehicleType.CAR, new FirstHourFlatPricing(40.0, 30.0),
                        VehicleType.TRUCK, new FirstHourFlatPricing(100.0, 80.0)),
                clock);

        // E3: bike slots 2-3 fill, the third bike falls back to car slot 4
        for (int i = 1; i <= 3; i++) {
            Ticket t = service.park(new Vehicle(VehicleType.BIKE, "BIKE-" + i, "red"));
            System.out.println("Bike " + i + " -> " + t.slotType() + " slot " + t.slot());
        }
        service.setBikeSharing(true);
        Ticket shared = service.park(new Vehicle(VehicleType.BIKE, "BIKE-4", "red"));
        System.out.println("Busy mode, bike 4 shares slot " + shared.slot());

        // E3: the car can't join a slot holding bikes, so it takes the truck slot
        Ticket car = service.park(new Vehicle(VehicleType.CAR, "KA-01-DB-1234", "black"));
        System.out.println("Car -> " + car.slotType() + " slot " + car.slot());

        // E4: lost ticket and duplicate plate
        System.out.println("Lost ticket lookup: " + service.ticketForRegistration("KA-01-DB-1234").id());
        try {
            service.park(new Vehicle(VehicleType.CAR, "KA-01-DB-1234", "black"));
        } catch (DomainException e) {
            System.out.println("Same plate again: " + e.getMessage());
        }

        // E1: car pays Rs 40 for the first hour and Rs 30 for each started hour after
        clock.advance(Duration.ofMinutes(125));
        Receipt receipt = service.unpark(car.id());
        System.out.println("Car fee after " + receipt.duration().toMinutes() + " min: " + receipt.formattedFee());
        System.out.println("Occupied car slots: " + service.occupiedSlots(SlotType.CAR));
    }
}
