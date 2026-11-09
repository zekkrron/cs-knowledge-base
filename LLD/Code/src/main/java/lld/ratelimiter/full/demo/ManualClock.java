package lld.ratelimiter.full.demo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A {@link Clock} that only moves when told to. Services take a Clock; production passes
 * {@code Clock.systemUTC()}, the demo and tests pass this and call {@link #advance}.
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