package lld.parkinglot.v0.cli;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import lld.parkinglot.v0.allocation.LowestFloorFirstStrategy;
import lld.parkinglot.v0.demo.ManualClock;
import lld.parkinglot.v0.exception.ConflictException;
import lld.parkinglot.v0.exception.ValidationException;
import lld.parkinglot.v0.model.ParkingLot;
import lld.parkinglot.v0.model.Receipt;
import lld.parkinglot.v0.model.Ticket;
import lld.parkinglot.v0.model.Vehicle;
import lld.parkinglot.v0.model.VehicleType;
import lld.parkinglot.v0.pricing.HourlyPricing;
import lld.parkinglot.v0.service.ParkingLotService;

/**
 * CLI in the workat.tech / Swiggy machine-coding format. Only parses and formats; all rules
 * live in the service. Fee is Rs 30 per started hour.
 * <pre>
 * create_parking_lot PR1234 2 6
 * park_vehicle CAR KA-01-DB-1234 black
 * unpark_vehicle PR1234_1_4
 * display free_count|free_slots|occupied_slots CAR
 * advance_minutes 125            (demo clock only)
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
                .on("advance_minutes", this::advance);
    }

    private String create(String[] a) {
        ValidationException.require(a.length == 3, "Usage: create_parking_lot <id> <floors> <slotsPerFloor>");
        int floors = number(a[1], "floors");
        int slots = number(a[2], "slotsPerFloor");
        service = new ParkingLotService(new ParkingLot(a[0], floors, slots), new LowestFloorFirstStrategy(),
                new HourlyPricing(30_00), clock);
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
        ValidationException.require(a.length == 2, "Usage: display <free_count|free_slots|occupied_slots> <type>");
        VehicleType type = VehicleType.parse(a[1]);
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

    private String advance(String[] a) {
        ValidationException.require(a.length == 1, "Usage: advance_minutes <minutes>");
        if (!(clock instanceof ManualClock manual)) {
            throw new ConflictException("Clock can only be moved in demo mode");
        }
        manual.advance(Duration.ofMinutes(number(a[0], "minutes")));
        return "";
    }

    private static void format(StringJoiner out, String prefix, VehicleType type, Map<Integer, List<Integer>> byFloor) {
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
            create_parking_lot PR1234 2 6
            park_vehicle CAR KA-01-DB-1234 black
            park_vehicle TRUCK KA-02-T-0001 white
            display free_count CAR
            advance_minutes 125
            unpark_vehicle PR1234_1_4
            unpark_vehicle PR1234_1_4
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
