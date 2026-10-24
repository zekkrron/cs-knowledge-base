package lld.parkinglot.full;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;


/**
 * CLI for the full version. Same commands as V0, plus:
 * <pre>
 * create_parking_lot PR1234 2 6 [EV slots, e.g. 4,5]   (E2: optional EV charger slot numbers)
 * park_vehicle EV_CAR KA-03-EV-0001 white              (E2)
 * unpark_vehicle PR1234_1_4_1                          (E1: prints duration and fee)
 * set_strategy SPREAD | LOWEST_FLOOR                   (E3)
 * advance_minutes 125                                  (demo only: moves the clock)
 * </pre>
 */
public final class ParkingLotApp {

    private final Clock clock;
    private ParkingLotService service;

    public ParkingLotApp(Clock clock) {
        this.clock = clock;
    }

    public CommandLineDriver driver() {
        return new CommandLineDriver(System.out)
                .on("create_parking_lot", this::create)
                .on("park_vehicle", this::park)
                .on("unpark_vehicle", this::unpark)
                .on("display", this::display)
                .on("set_strategy", this::setStrategy)
                .on("advance_minutes", this::advance);
    }

    /** E1: one pricing strategy per vehicle type. A new scheme is a new class plus one put. */
    static Map<VehicleType, PricingStrategy> defaultPricing() {
        Map<VehicleType, PricingStrategy> pricing = new EnumMap<>(VehicleType.class);
        pricing.put(VehicleType.BIKE, new HourlyPricing(10_00));
        pricing.put(VehicleType.CAR, new FirstHourFlatPricing(40_00, 30_00));
        pricing.put(VehicleType.EV_CAR, new FirstHourFlatPricing(40_00, 30_00));
        pricing.put(VehicleType.TRUCK, new HourlyPricing(100_00));
        return pricing;
    }

    private String create(String[] a) {
        ValidationException.require(a.length == 3 || a.length == 4,
                "Usage: create_parking_lot <id> <floors> <slotsPerFloor> [evSlotNumbers]");
        int floors = parseInt(a[1], "floors");
        int slots = parseInt(a[2], "slotsPerFloor");
        FloorLayout layout = FloorLayout.standard();
        if (a.length == 4) {
            List<Integer> ev = Arrays.stream(a[3].split(",")).map(s -> parseInt(s, "evSlot")).toList();
            FloorLayout base = layout;
            layout = n -> ev.contains(n) ? SlotType.EV_CHARGING : base.typeOf(n);
        }
        ParkingLot lot = new ParkingLot(a[0], floors, slots, layout);
        service = new ParkingLotService(lot, CompatibilityRules.defaults(), new LowestFloorFirstStrategy(),
                defaultPricing(), clock);
        return "Created parking lot with " + floors + " floors and " + slots + " slots per floor";
    }

    private String park(String[] a) {
        ValidationException.require(a.length == 3, "Usage: park_vehicle <type> <regNo> <color>");
        Ticket t = requireService().park(new Vehicle(VehicleType.parse(a[0]), a[1], a[2]));
        return "Parked vehicle. Ticket ID: " + t.id() + " (" + t.slotType() + " slot)";
    }

    private String unpark(String[] a) {
        ValidationException.require(a.length == 1, "Usage: unpark_vehicle <ticketId>");
        Receipt r = requireService().unpark(a[0]);
        Vehicle v = r.ticket().vehicle();
        return "Unparked vehicle with Registration Number: " + v.registrationNumber() + " and Color: " + v.color()
                + ". Parked " + r.duration().toMinutes() + " min, fee " + r.feeRupees();
    }

    private String setStrategy(String[] a) {
        ValidationException.require(a.length == 1, "Usage: set_strategy <LOWEST_FLOOR|SPREAD>");
        SlotAllocationStrategy next = switch (a[0].toUpperCase()) {
            case "LOWEST_FLOOR" -> new LowestFloorFirstStrategy();
            case "SPREAD" -> new SpreadAcrossFloorsStrategy();
            default -> throw new ValidationException("Unknown strategy: " + a[0]);
        };
        requireService().switchAllocation(next);
        return "Allocation strategy: " + next.name();
    }

    private String advance(String[] a) {
        if (!(clock instanceof ManualClock manual)) {
            throw new ConflictException("Clock can only be moved in demo mode");
        }
        manual.advance(Duration.ofMinutes(parseInt(a[0], "minutes")));
        return "";
    }

    private String display(String[] a) {
        ValidationException.require(a.length == 2, "Usage: display <free_count|free_slots|occupied_slots> <slotType>");
        SlotType type = SlotType.parse(a[1]);
        StringJoiner out = new StringJoiner(System.lineSeparator());
        switch (a[0]) {
            case "free_count" -> requireService().freeSlots(type).forEach((floor, slots) ->
                    out.add("No. of free slots for " + type + " on Floor " + floor + ": " + slots.size()));
            case "free_slots" -> format(out, "Free slots for ", type, requireService().freeSlots(type));
            case "occupied_slots" -> format(out, "Occupied slots for ", type, requireService().occupiedSlots(type));
            default -> throw new ValidationException("Unknown display type: " + a[0]);
        }
        return out.toString();
    }

    private static void format(StringJoiner out, String label, SlotType type, Map<Integer, List<Integer>> byFloor) {
        byFloor.forEach((floor, slots) -> out.add(label + type + " on Floor " + floor + ": "
                + slots.stream().map(String::valueOf).collect(Collectors.joining(","))));
    }

    private static int parseInt(String raw, String field) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(field + " must be a number: " + raw);
        }
    }

    private ParkingLotService requireService() {
        if (service == null) {
            throw new ConflictException("Create a parking lot first");
        }
        return service;
    }

    static final String DEMO = """
            create_parking_lot PR1234 2 6 5
            park_vehicle CAR KA-01-DB-1234 black
            park_vehicle EV_CAR KA-03-EV-0001 white
            park_vehicle EV_CAR KA-03-EV-0002 blue
            display free_slots CAR
            display free_slots EV_CHARGING
            advance_minutes 125
            unpark_vehicle PR1234_1_4_1
            unpark_vehicle PR1234_1_4_1
            set_strategy SPREAD
            park_vehicle CAR KA-05-XY-9999 grey
            exit
            """;

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("--stdin")) {
            new ParkingLotApp(Clock.systemUTC()).driver().run(new InputStreamReader(System.in));
        } else {
            new ParkingLotApp(new ManualClock(Instant.parse("2026-10-24T10:00:00Z")))
                    .driver().run(new StringReader(DEMO));
        }
    }
}
