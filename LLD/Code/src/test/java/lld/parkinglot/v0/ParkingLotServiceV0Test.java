package lld.parkinglot.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class ParkingLotServiceV0Test {

    private ParkingLotService service;

    @BeforeEach
    void setUp() {
        service = new ParkingLotService(new ParkingLot("PR1234", 2, 6), new LowestFloorFirstStrategy());
    }

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
    void unparkFreesTheSlotAndTicketCannotBeUsedTwice() {
        Ticket t = service.park(car("KA-01"));
        assertEquals("KA-01", service.unpark(t.id()).registrationNumber());
        assertEquals(List.of(4, 5, 6), service.freeSlots(VehicleType.CAR).get(1));
        assertThrows(NotFoundException.class, () -> service.unpark(t.id()));
    }

    @Test
    void unknownTicketIsInvalid() {
        assertThrows(NotFoundException.class, () -> service.unpark("PR1234_9_9"));
    }

    @Test
    void vehicleValidation() {
        assertThrows(ValidationException.class, () -> new Vehicle(VehicleType.CAR, " ", "black"));
        assertThrows(ValidationException.class, () -> VehicleType.parse("BUS"));
    }

    @Test
    void cliPrintsPromptFormat() {
        var driver = new ParkingLotApp().driver();
        driver.execute("create_parking_lot PR1234 2 6");
        assertEquals("Parked vehicle. Ticket ID: PR1234_1_4", driver.execute("park_vehicle CAR KA-01-DB-1234 black"));
        assertEquals("Invalid Ticket", driver.execute("unpark_vehicle PR1234_2_2"));
        assertEquals("No. of free slots for CAR on Floor 1: 2" + System.lineSeparator()
                + "No. of free slots for CAR on Floor 2: 3", driver.execute("display free_count CAR"));
    }

    private static Vehicle car(String reg) {
        return new Vehicle(VehicleType.CAR, reg, "black");
    }
}
