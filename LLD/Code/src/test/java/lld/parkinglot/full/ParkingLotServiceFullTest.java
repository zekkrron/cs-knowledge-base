package lld.parkinglot.full;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import lld.parkinglot.full.allocation.LowestFloorFirstStrategy;
import lld.parkinglot.full.cli.ParkingLotCli;
import lld.parkinglot.full.demo.ManualClock;
import lld.parkinglot.full.exception.ConflictException;
import lld.parkinglot.full.exception.DomainException;
import lld.parkinglot.full.exception.NotFoundException;
import lld.parkinglot.full.model.ParkingLot;
import lld.parkinglot.full.model.SlotType;
import lld.parkinglot.full.model.Ticket;
import lld.parkinglot.full.model.Vehicle;
import lld.parkinglot.full.model.VehicleType;
import lld.parkinglot.full.pricing.FirstHourFlatPricing;
import lld.parkinglot.full.pricing.HourlyPricing;
import lld.parkinglot.full.rules.CompatibilityRules;
import lld.parkinglot.full.service.ParkingLotService;

class ParkingLotServiceFullTest {

    private final ManualClock clock = new ManualClock(Instant.parse("2026-10-27T10:00:00Z"));

    private ParkingLotService service(int floors, int slots) {
        return new ParkingLotService(new ParkingLot("PR1234", floors, slots), CompatibilityRules.defaults(),
                new LowestFloorFirstStrategy(),
                Map.of(VehicleType.BIKE, new HourlyPricing(10_00),
                        VehicleType.CAR, new FirstHourFlatPricing(40_00, 30_00),
                        VehicleType.TRUCK, new FirstHourFlatPricing(100_00, 80_00)),
                clock);
    }

    // ---------- E1: pricing per vehicle type ----------

    @Test
    void e1CarPaysFlatFirstHourThenHourlyAndBikePaysHourly() {
        ParkingLotService s = service(1, 6);
        Ticket car = s.park(car("C1"));
        Ticket bike = s.park(bike("B1"));
        clock.advance(Duration.ofMinutes(125));
        assertEquals(40_00 + 2 * 30_00, s.unpark(car.id()).feePaise());
        assertEquals(3 * 10_00, s.unpark(bike.id()).feePaise());
    }

    // ---------- E2: concurrent gates ----------

    @Test
    void e2ManyGatesNeverHandOutOneSlotTwice() throws Exception {
        ParkingLotService s = service(10, 20); // per floor: 1 truck + 17 car slots fit a car = 180
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        Set<String> claimed = ConcurrentHashMap.newKeySet();
        AtomicInteger parked = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        AtomicInteger duplicateSlots = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            String reg = "C" + i;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    Ticket t = s.park(car(reg));
                    parked.incrementAndGet();
                    if (!claimed.add(t.floor() + "-" + t.slot())) {
                        duplicateSlots.incrementAndGet();
                    }
                } catch (ConflictException e) {
                    full.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertEquals(180, parked.get());
        assertEquals(220, full.get());
        assertEquals(0, duplicateSlots.get());
        assertEquals(180, s.activeTicketCount());
    }

    @Test
    void e2TwoExitsOnOneTicketBillOnce() throws Exception {
        ParkingLotService s = service(1, 6);
        Ticket t = s.park(car("C1"));
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger invalid = new AtomicInteger();
        runTogether(8, () -> {
            try {
                s.unpark(t.id());
                ok.incrementAndGet();
            } catch (NotFoundException e) {
                invalid.incrementAndGet();
            }
            return null;
        });
        assertEquals(1, ok.get());
        assertEquals(7, invalid.get());
    }

    @Test
    void e2SamePlateAtTwoGatesParksOnce() throws Exception {
        ParkingLotService s = service(2, 20);
        AtomicInteger ok = new AtomicInteger();
        runTogether(8, () -> {
            try {
                s.park(car("SAME"));
                ok.incrementAndGet();
            } catch (ConflictException e) {
                // expected for all but one
            }
            return null;
        });
        assertEquals(1, ok.get());
        assertEquals(1, s.activeTicketCount());
    }

    @Test
    void e2TicketIdsAreUniqueWhenASlotIsReused() {
        ParkingLotService s = service(1, 4);
        Ticket first = s.park(car("C1"));
        s.unpark(first.id());
        Ticket second = s.park(car("C2"));
        assertEquals(first.slot(), second.slot());
        assertTrue(!first.id().equals(second.id()));
        assertThrows(NotFoundException.class, () -> s.unpark(first.id()));
    }

    // ---------- E3: compatibility and bike sharing ----------

    @Test
    void e3SmallerVehiclesMayUseLargerSlotsNeverTheReverse() {
        ParkingLotService s = service(1, 4); // slot 1 truck, 2-3 bike, 4 car
        assertEquals(SlotType.BIKE, s.park(bike("B1")).slotType());
        assertEquals(SlotType.BIKE, s.park(bike("B2")).slotType());
        assertEquals(SlotType.CAR, s.park(bike("B3")).slotType());   // bike falls back to car slot
        assertEquals(SlotType.TRUCK, s.park(car("C1")).slotType());  // car falls back to truck slot
        assertThrows(ConflictException.class, () -> s.park(car("C2"))); // never a bike slot
        assertThrows(ConflictException.class, () -> s.park(truck("T1")));
    }

    @Test
    void e3TruckOnlyUsesTruckSlots() {
        ParkingLotService s = service(1, 6);
        s.park(truck("T1"));
        assertThrows(ConflictException.class, () -> s.park(truck("T2")));
    }

    @Test
    void e3BikeSharingPutsFourBikesInOneCarSlotAndNoCarJoins() {
        ParkingLotService s = service(1, 4);
        s.park(bike("B1"));
        s.park(bike("B2"));
        s.setBikeSharing(true);
        Set<Integer> slots = new HashSet<>();
        for (int i = 3; i <= 6; i++) {
            slots.add(s.park(bike("B" + i)).slot());
        }
        assertEquals(Set.of(4), slots);                              // all four share car slot 4
        assertEquals(SlotType.TRUCK, s.park(car("C1")).slotType());  // the car can't join the bikes
        assertThrows(ConflictException.class, () -> s.park(bike("B7"))); // slot 4 holds at most 4
    }

    // ---------- E4: lookups ----------

    @Test
    void e4LookupsByPlateAndColourAndDuplicatePlateRejected() {
        ParkingLotService s = service(2, 6);
        Ticket white = s.park(new Vehicle(VehicleType.CAR, "W1", "white"));
        s.park(new Vehicle(VehicleType.CAR, "K1", "black"));
        Ticket white2 = s.park(new Vehicle(VehicleType.CAR, "W2", "WHITE"));

        assertEquals(white.id(), s.ticketForRegistration("W1").id());
        assertEquals(List.of("W1", "W2"),
                s.ticketsWithColor("white").stream().map(t -> t.vehicle().registrationNumber()).toList());
        assertEquals(white2.slot(), s.ticketsWithColor("white").get(1).slot());
        assertThrows(ConflictException.class, () -> s.park(new Vehicle(VehicleType.CAR, "W1", "white")));

        s.unpark(white.id());
        assertThrows(NotFoundException.class, () -> s.ticketForRegistration("W1"));
        s.park(new Vehicle(VehicleType.CAR, "W1", "white")); // can come back after leaving
    }

    // ---------- CLI ----------

    @Test
    void cliPrintsExtensionCommands() {
        var driver = new ParkingLotCli(clock).driver();
        driver.execute("create_parking_lot PR1234 1 4");
        assertEquals("Parked vehicle. Ticket ID: PR1234_1_4_1", driver.execute("park_vehicle CAR KA-01 black"));
        assertEquals("Vehicle already parked", driver.execute("park_vehicle CAR KA-01 black"));
        assertEquals("Vehicle KA-01 is on Floor 1 Slot 4. Ticket ID: PR1234_1_4_1", driver.execute("find_vehicle KA-01"));
        assertEquals("KA-01", driver.execute("registrations_by_color black"));
        assertEquals("1-4", driver.execute("slots_by_color black"));
        assertEquals("Bike sharing on", driver.execute("bike_sharing on"));
        assertEquals("Vehicle not found", driver.execute("find_vehicle NOPE"));
        driver.execute("advance_minutes 125");
        assertEquals("Unparked vehicle with Registration Number: KA-01 and Color: black"
                + System.lineSeparator() + "Fee: Rs 100.00", driver.execute("unpark_vehicle PR1234_1_4_1"));
    }

    private static void runTogether(int threads, Callable<Void> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        for (Future<Void> f : futures) {
            try {
                f.get();
            } catch (java.util.concurrent.ExecutionException e) {
                if (!(e.getCause() instanceof DomainException)) {
                    throw e;
                }
            }
        }
        pool.shutdown();
    }

    private static Vehicle car(String reg) {
        return new Vehicle(VehicleType.CAR, reg, "black");
    }

    private static Vehicle bike(String reg) {
        return new Vehicle(VehicleType.BIKE, reg, "red");
    }

    private static Vehicle truck(String reg) {
        return new Vehicle(VehicleType.TRUCK, reg, "white");
    }
}
