package lld.parkinglot.full.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import lld.parkinglot.full.exception.DomainException;

/**
 * Line-based command runner: {@code park_vehicle CAR KA-01-1234 black}. Each command maps to a
 * handler that returns the text to print. A {@link DomainException} prints as a normal message,
 * so "run it with an invalid ticket" shows a clean line instead of a stack trace.
 */
public final class CommandLineDriver {

    private final Map<String, Function<String[], String>> handlers = new LinkedHashMap<>();
    private final PrintStream out;

    public CommandLineDriver(PrintStream out) {
        this.out = out;
    }

    public CommandLineDriver on(String command, Function<String[], String> handler) {
        handlers.put(command, handler);
        return this;
    }

    /** Runs one line and returns what was printed. */
    public String execute(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return "";
        }
        String[] parts = trimmed.split("\\s+");
        Function<String[], String> handler = handlers.get(parts[0]);
        String result;
        if (handler == null) {
            result = "Unknown command: " + parts[0];
        } else {
            try {
                result = handler.apply(Arrays.copyOfRange(parts, 1, parts.length));
            } catch (DomainException e) {
                result = e.getMessage();
            }
        }
        if (!result.isEmpty()) {
            out.println(result);
        }
        return result;
    }

    /** Runs every line until EOF or {@code exit}. */
    public void run(Reader input) throws IOException {
        try (BufferedReader reader = new BufferedReader(input)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("exit")) {
                    return;
                }
                execute(line);
            }
        }
    }
}