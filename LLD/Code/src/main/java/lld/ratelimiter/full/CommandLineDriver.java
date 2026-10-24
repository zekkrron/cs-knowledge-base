package lld.ratelimiter.full;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Line-based command runner for the demo: {@code park_vehicle CAR KA-01-1234 black}.
 *
 * <p>Each command maps to a handler that takes the arguments and returns the text to
 * print. A {@link DomainException} is printed as a normal error line, so "run it with an
 * invalid ticket" in the review shows a clean message; any other exception is a bug and
 * propagates.
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

    /** Runs one line and returns what was printed (handy in tests). */
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
