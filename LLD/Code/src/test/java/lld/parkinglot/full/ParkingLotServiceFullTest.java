package lld.parkinglot.full;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;


class ParkingLotServiceFullTest {

    private final ManualClock clock = new ManualClock(Instant.parse("2026-10-24T10:00:00Z"));

    private ParkingLotService service(ParkingLot lot) {
        return new ParkingLotService(lot, CompatibilityRules.defaults(), new LowestFloorFirstStrategy(),
                ParkingLotApp.defaultPricing(), clock);
    }

    private ParkingLotService standard() {
        return service(new ParkingLot("PR1234", 2, 6, FloorLayout.standard()));
    }

    // E1 ---------------------------------------------------------------

    @Test
    void e1_carPaysFirstHourFlatThenPerStartedHour() {
        ParkingLotService s = standard();
        Ticket t = s.park(new Vehicle(VehicleType.CAR, "KA-01", "black"));
        clock.advance(Duration.ofMinutes(125)); // 3 started hours
        Receipt r = s.unpark(t.id());
        assertEquals(40_00 + 2 * 30_00, r.feePaise());
        assertEquals("Rs 100.00", r.feeRupees());
    }

    @Test
    void e1_zeroMinutesStillPaysOneHour() {
        ParkingLotService s = standard();
        Ticket t = s.park(new Vehicle(VehicleType.BIKE, "KA-02", "red"));
        assertEquals(10_00, s.unpark(t.id()).feePaise());
    }

    // E2 ---------------------------------------------------------------

    @Test
    void e2_evPrefersChargerThenFallsBackToCarSlot() {
        FloorLayout layout = FloorLayout.of(List.of(SlotType.TRUCK, SlotType.BIKE, SlotType.CAR, SlotType.EV_CHARGING));
        ParkingLotService s = service(new ParkingLot("L", 1, 4, layout));
        assertEquals(SlotType.EV_CHARGING, s.park(new Vehicle(VehicleType.EV_CAR, "EV1", "white")).slotType());
        assertEquals(SlotType.CAR, s.park(new Vehicle(VehicleType.EV_CAR, "EV2", "white")).slotType());
        assertThrows(ConflictException.class, () -> s.park(new Vehicle(VehicleType.CAR, "C1", "black")));
    }

    @Test
    void e2_normalCarNeverTakesAChargingSlot() {
        FloorLayout layout = FloorLayout.of(List.of(SlotType.EV_CHARGING));
        ParkingLotService s = service(new ParkingLot("L", 1, 1, layout));
        assertThrows(ConflictException.class, () -> s.park(new Vehicle(VehicleType.CAR, "C1", "black")));
    }

    // E3 ---------------------------------------------------------------

    @Test
    void e3_spreadStrategySendsNextCarToTheEmptierFloor() {
        ParkingLotService s = standard();
        s.park(new Vehicle(VehicleType.CAR, "C1", "black")); // floor 1
        s.switchAllocation(new SpreadAcrossFloorsStrategy());
        assertEquals(2, s.park(new Vehicle(VehicleType.CAR, "C2", "black")).floor());
    }

    // E4 ---------------------------------------------------------------

    @Test
    void e4_ticketIdsAreNeverReusedWhenASlotIsReused() {
        ParkingLotService s = standard();
        Ticket first = s.park(new Vehicle(VehicleType.CAR, "C1", "black"));
        s.unpark(first.id());
        Ticket second = s.park(new Vehicle(VehicleType.CAR, "C2", "black"));
        assertEquals(first.slot(), second.slot());
        assertNotEquals(first.id(), second.id());
        assertThrows(NotFoundException.class, () -> s.unpark(first.id()));
    }

    @Test
    void e4_manyGatesNeverDoubleAllocateASlot() throws InterruptedException {
        // 10 floors x 20 slots: 17 car slots per floor = 170 car slots, 400 cars racing.
        ParkingLotService s = service(new ParkingLot("BIG", 10, 20, FloorLayout.standard()));
        int cars = 400;
        ExecutorService gates = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        Set<String> slotsTaken = ConcurrentHashMap.newKeySet();
        AtomicInteger parked = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        for (int i = 0; i < cars; i++) {
            String reg = "CAR-" + i;
            gates.submit(() -> {
                start.await();
                try {
                    Ticket t = s.park(new Vehicle(VehicleType.CAR, reg, "black"));
                    assertTrue(slotsTaken.add(t.floor() + "-" + t.slot()), "slot handed out twice");
                    parked.incrementAndGet();
                } catch (ConflictException full) {
                    rejected.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        gates.shutdown();
        assertTrue(gates.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(170, parked.get());
        assertEquals(230, rejected.get());
        assertEquals(170, slotsTaken.size());
        assertEquals(170, s.activeTicketCount());
    }

    @Test
    void e4_twoExitGatesWithTheSameTicketOnlyOneSucceeds() throws InterruptedException {
        ParkingLotService s = standard();
        Ticket t = s.park(new Vehicle(VehicleType.CAR, "C1", "black"));
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger invalid = new AtomicInteger();
        Thread[] gates = new Thread[8];
        for (int i = 0; i < gates.length; i++) {
            gates[i] = new Thread(() -> {
                try {
                    s.unpark(t.id());
                    ok.incrementAndGet();
                } catch (NotFoundException e) {
                    invalid.incrementAndGet();
                }
            });
        }
        for (Thread g : gates) g.start();
        for (Thread g : gates) g.join();
        assertEquals(1, ok.get());
        assertEquals(7, invalid.get());
    }
}
