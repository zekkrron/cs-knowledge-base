package lld.parkinglot.full.cli;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

import lld.parkinglot.full.allocation.LowestFloorFirstStrategy;
import lld.parkinglot.full.demo.ManualClock;
import lld.parkinglot.full.exception.ConflictException;
import lld.parkinglot.full.exception.ValidationException;
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

/**
 * V0's commands plus the extension commands. Only parses and formats; all rules live in the
 * service. Prices (E1): bike Rs 10 per started hour; car Rs 40 first hour then Rs 30; truck
 * Rs 100 first hour then Rs 80.
 * <pre>
 * create_parking_lot PR1234 2 6
 * park_vehicle CAR KA-01-DB-1234 black
 * unpark_vehicle PR1234_1_4_1
 * display free_count|free_slots|occupied_slots CAR     (slot type)
 * bike_sharing on|off                                  (E3)
 * find_vehicle KA-01-DB-1234                           (E4, lost ticket)
 * registrations_by_color black                         (E4)
 * slots_by_color black                                 (E4)
 * advance_minutes 125                                  (demo clock only)
 * </pre>
 */
public final class ParkingLotCli {

    private final Clock clock;
    private ParkingLotService service;

    public ParkingLotCli(Clock clock) {
        this.clock = clock;
    }

    public CommandLineDriver driver() {
        return new CommandLineDriver(System.out)
                .on("create_parking_lot", this::create)
                .on("park_vehicle", this::park)
                .on("unpark_vehicle", this::unpark)
                .on("display", this::display)
                .on("bike_sharing", this::bikeSharing)
                .on("find_vehicle", this::find)
                .on("registrations_by_color", a -> byColor(a, t -> t.vehicle().registrationNumber()))
                .on("slots_by_color", a -> byColor(a, t -> t.floor() + "-" + t.slot()))
                .on("advance_minutes", this::advance);
    }

    private String create(String[] a) {
        ValidationException.require(a.length == 3, "Usage: create_parking_lot <id> <floors> <slotsPerFloor>");
        int floors = number(a[1], "floors");
        int slots = number(a[2], "slotsPerFloor");
        service = new ParkingLotService(new ParkingLot(a[0], floors, slots), CompatibilityRules.defaults(),
                new LowestFloorFirstStrategy(),
                Map.of(VehicleType.BIKE, new HourlyPricing(10_00),
                        VehicleType.CAR, new FirstHourFlatPricing(40_00, 30_00),
                        VehicleType.TRUCK, new FirstHourFlatPricing(100_00, 80_00)),
                clock);
        return "Created parking lot with " + floors + " floors and " + slots + " slots per floor";
    }

    private String park(String[] a) {
        ValidationException.require(a.length == 3, "Usage: park_vehicle <type> <regNo> <color>");
        Ticket ticket = requireService().park(new Vehicle(VehicleType.parse(a[0]), a[1], a[2]));
        return "Parked vehicle. Ticket ID: " + ticket.id();
    }

    private String unpark(String[] a) {
        ValidationException.require(a.length == 1, "Usage: unpark_vehicle <ticketId>");
        Receipt r = requireService().unpark(a[0]);
        Vehicle v = r.ticket().vehicle();
        return "Unparked vehicle with Registration Number: " + v.registrationNumber() + " and Color: " + v.color()
                + System.lineSeparator() + "Fee: " + r.feeRupees();
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

    private String bikeSharing(String[] a) {
        ValidationException.require(a.length == 1 && (a[0].equals("on") || a[0].equals("off")),
                "Usage: bike_sharing on|off");
        requireService().setBikeSharing(a[0].equals("on"));
        return "Bike sharing " + a[0];
    }

    private String find(String[] a) {
        ValidationException.require(a.length == 1, "Usage: find_vehicle <regNo>");
        Ticket t = requireService().ticketForRegistration(a[0]);
        return "Vehicle " + a[0] + " is on Floor " + t.floor() + " Slot " + t.slot() + ". Ticket ID: " + t.id();
    }

    private String byColor(String[] a, Function<Ticket, String> field) {
        ValidationException.require(a.length == 1, "Usage: <command> <color>");
        List<Ticket> tickets = requireService().ticketsWithColor(a[0]);
        if (tickets.isEmpty()) {
            return "No vehicles with color " + a[0];
        }
        return tickets.stream().map(field).collect(Collectors.joining(", "));
    }

    private String advance(String[] a) {
        ValidationException.require(a.length == 1, "Usage: advance_minutes <minutes>");
        if (!(clock instanceof ManualClock manual)) {
            throw new ConflictException("Clock can only be moved in demo mode");
        }
        manual.advance(Duration.ofMinutes(number(a[0], "minutes")));
        return "";
    }

    private static void format(StringJoiner out, String prefix, SlotType type, Map<Integer, List<Integer>> byFloor) {
        byFloor.forEach((floor, slots) -> out.add(prefix + type + " on Floor " + floor + ": "
                + slots.stream().map(String::valueOf).collect(Collectors.joining(","))));
    }

    private static int number(String raw, String field) {
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

    static final String SAMPLE = """
            create_parking_lot PR1234 1 4
            park_vehicle BIKE KA-01-B-0001 red
            park_vehicle BIKE KA-01-B-0002 red
            park_vehicle BIKE KA-01-B-0003 red
            bike_sharing on
            park_vehicle BIKE KA-01-B-0004 red
            park_vehicle CAR KA-01-DB-1234 black
            find_vehicle KA-01-B-0004
            registrations_by_color red
            advance_minutes 125
            unpark_vehicle PR1234_1_4_3
            park_vehicle BIKE KA-01-B-0001 red
            exit
            """;

    /** No args: runs the sample. With "stdin": reads commands from standard input. */
    public static void main(String[] args) throws IOException {
        ParkingLotCli cli = new ParkingLotCli(new ManualClock(Instant.parse("2026-10-27T10:00:00Z")));
        if (args.length > 0 && args[0].equals("stdin")) {
            cli.driver().run(new InputStreamReader(System.in));
        } else {
            cli.driver().run(new StringReader(SAMPLE));
        }
    }
}
