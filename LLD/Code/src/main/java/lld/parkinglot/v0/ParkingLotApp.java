package lld.parkinglot.v0;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;


/**
 * CLI from the prompt. Run with no arguments for the built-in demo, or {@code --stdin}
 * to type commands.
 *
 * <pre>
 * create_parking_lot PR1234 2 6
 * park_vehicle CAR KA-01-DB-1234 black
 * unpark_vehicle PR1234_1_4
 * display free_count CAR | free_slots CAR | occupied_slots CAR
 * exit
 * </pre>
 */
public final class ParkingLotApp {

    private ParkingLotService service;

    public CommandLineDriver driver() {
        return new CommandLineDriver(System.out)
                .on("create_parking_lot", this::create)
                .on("park_vehicle", this::park)
                .on("unpark_vehicle", this::unpark)
                .on("display", this::display);
    }

    private String create(String[] a) {
        ValidationException.require(a.length == 3, "Usage: create_parking_lot <id> <floors> <slotsPerFloor>");
        ParkingLot lot = new ParkingLot(a[0], parseInt(a[1], "floors"), parseInt(a[2], "slotsPerFloor"));
        service = new ParkingLotService(lot, new LowestFloorFirstStrategy());
        return "Created parking lot with " + a[1] + " floors and " + a[2] + " slots per floor";
    }

    private String park(String[] a) {
        ValidationException.require(a.length == 3, "Usage: park_vehicle <type> <regNo> <color>");
        Ticket ticket = requireService().park(new Vehicle(VehicleType.parse(a[0]), a[1], a[2]));
        return "Parked vehicle. Ticket ID: " + ticket.id();
    }

    private String unpark(String[] a) {
        ValidationException.require(a.length == 1, "Usage: unpark_vehicle <ticketId>");
        Vehicle v = requireService().unpark(a[0]);
        return "Unparked vehicle with Registration Number: " + v.registrationNumber() + " and Color: " + v.color();
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

    private static void format(StringJoiner out, String label, VehicleType type, Map<Integer, List<Integer>> byFloor) {
        byFloor.forEach((floor, slots) -> out.add(label + type + " on Floor " + floor + ": "
                + slots.stream().map(String::valueOf).collect(Collectors.joining(","))));
    }

    private static int parseInt(String raw, String field) {
        try {
            return Integer.parseInt(raw);
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
            create_parking_lot PR1234 2 6
            display free_count CAR
            display free_slots BIKE
            park_vehicle CAR KA-01-DB-1234 black
            park_vehicle CAR KA-02-CB-1334 red
            park_vehicle TRUCK KA-04-AB-1111 white
            park_vehicle TRUCK KA-05-AB-2222 white
            park_vehicle TRUCK KA-06-AB-3333 white
            display occupied_slots CAR
            unpark_vehicle PR1234_1_4
            unpark_vehicle PR1234_1_4
            park_vehicle BUS KA-09-ZZ-0000 yellow
            display free_slots CAR
            exit
            """;

    public static void main(String[] args) throws IOException {
        ParkingLotApp app = new ParkingLotApp();
        if (args.length > 0 && args[0].equals("--stdin")) {
            app.driver().run(new InputStreamReader(System.in));
        } else {
            app.driver().run(new StringReader(DEMO));
        }
    }
}
