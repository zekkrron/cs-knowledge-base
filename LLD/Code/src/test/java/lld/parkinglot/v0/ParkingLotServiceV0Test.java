package lld.parkinglot.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import lld.parkinglot.v0.allocation.LowestFloorFirstStrategy;
import lld.parkinglot.v0.cli.ParkingLotCli;
import lld.parkinglot.v0.demo.ManualClock;
import lld.parkinglot.v0.exception.ConflictException;
import lld.parkinglot.v0.exception.NotFoundException;
import lld.parkinglot.v0.exception.ValidationException;
import lld.parkinglot.v0.model.ParkingLot;
import lld.parkinglot.v0.model.Receipt;
import lld.parkinglot.v0.model.Ticket;
import lld.parkinglot.v0.model.Vehicle;
import lld.parkinglot.v0.model.VehicleType;
import lld.parkinglot.v0.pricing.HourlyPricing;
import lld.parkinglot.v0.service.ParkingLotService;

class ParkingLotServiceV0Test {

    private final ManualClock clock = new ManualClock(Instant.parse("2026-10-27T10:00:00Z"));
    private final ParkingLotService service = new ParkingLotService(
            new ParkingLot("PR1234", 2, 6), new LowestFloorFirstStrategy(), new HourlyPricing(30_00), clock);

    @Test
    void parksInLowestFloorThenLowestSlotOfMatchingType() {
        assertEquals("PR1234_1_4", service.park(car("KA-01")).id());
        assertEquals("PR1234_1_5", service.park(car("KA-02")).id());
        assertEquals("PR1234_1_2", service.park(new Vehicle(VehicleType.BIKE, "KA-03", "red")).id());
        assertEquals("PR1234_1_1", service.park(new Vehicle(VehicleType.TRUCK, "KA-04", "white")).id());
        assertEquals("PR1234_2_1", service.park(new Vehicle(VehicleType.TRUCK, "KA-05", "white")).id());
    }

    @Test
    void rejectsWhenNoSlotOfThatTypeIsFree() {
        service.park(new Vehicle(VehicleType.TRUCK, "T1", "white"));
        service.park(new Vehicle(VehicleType.TRUCK, "T2", "white"));
        ConflictException e = assertThrows(ConflictException.class,
                () -> service.park(new Vehicle(VehicleType.TRUCK, "T3", "white")));
        assertEquals("Parking Lot Full", e.getMessage());
    }

    @Test
    void unparkBillsPerStartedHourFreesSlotAndTicketCannotBeReused() {
        Ticket t = service.park(car("KA-01"));
        clock.advance(Duration.ofMinutes(125));
        Receipt r = service.unpark(t.id());
        assertEquals(3 * 30_00, r.feePaise());
        assertEquals("Rs 90.00", r.feeRupees());
        assertEquals(List.of(4, 5, 6), service.freeSlots(VehicleType.CAR).get(1));
        assertThrows(NotFoundException.class, () -> service.unpark(t.id()));
    }

    @Test
    void zeroMinutesStillPaysOneHour() {
        Ticket t = service.park(car("KA-01"));
        assertEquals(30_00, service.unpark(t.id()).feePaise());
    }

    @Test
    void unknownTicketAndBadInput() {
        assertThrows(NotFoundException.class, () -> service.unpark("PR1234_9_9"));
        assertThrows(ValidationException.class, () -> new Vehicle(VehicleType.CAR, " ", "black"));
        assertThrows(ValidationException.class, () -> VehicleType.parse("BUS"));
        assertThrows(ValidationException.class, () -> new ParkingLot("X", 0, 6));
    }

    @Test
    void cliPrintsPromptFormat() {
        var driver = new ParkingLotCli(clock).driver();
        driver.execute("create_parking_lot PR1234 2 6");
        assertEquals("Parked vehicle. Ticket ID: PR1234_1_4", driver.execute("park_vehicle CAR KA-01-DB-1234 black"));
        assertEquals("Invalid Ticket", driver.execute("unpark_vehicle PR1234_2_2"));
        assertEquals("No. of free slots for CAR on Floor 1: 2" + System.lineSeparator()
                + "No. of free slots for CAR on Floor 2: 3", driver.execute("display free_count CAR"));
        assertEquals("Unparked vehicle with Registration Number: KA-01-DB-1234 and Color: black"
                + System.lineSeparator() + "Fee: Rs 30.00", driver.execute("unpark_vehicle PR1234_1_4"));
        assertEquals("floors must be a number: x", driver.execute("create_parking_lot P x 6"));
    }

    private static Vehicle car(String reg) {
        return new Vehicle(VehicleType.CAR, reg, "black");
    }
}
