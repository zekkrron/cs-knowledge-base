package lld.ratelimiter.v0;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A {@link Clock} that only moves when told to.
 *
 * <p>Services take a {@code java.time.Clock} in their constructor; production passes
 * {@code Clock.systemUTC()}, tests and the demo pass a ManualClock and call {@link #advance}
 * to test "refilled after 1 second" without sleeping.
 */
public final class ManualClock extends Clock {

    private volatile Instant now;
    private final ZoneId zone;

    public ManualClock(Instant start) {
        this(start, ZoneOffset.UTC);
    }

    private ManualClock(Instant start, ZoneId zone) {
        this.now = start;
        this.zone = zone;
    }

    public synchronized void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new ManualClock(now, newZone);
    }
}
