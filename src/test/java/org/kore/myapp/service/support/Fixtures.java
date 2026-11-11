package org.kore.myapp.service.support;

import org.kore.myapp.core.port.IdGenerator;
import org.kore.myapp.core.valueobject.Coordinate;
import org.kore.myapp.core.valueobject.Location;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic time, identifiers and places for unit tests (constitution Principle II).
 */
public final class Fixtures {

    public static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");

    public static final Location MONAS = new Location(new Coordinate(-6.1754, 106.8272), "Monas");
    public static final Location SENAYAN = new Location(new Coordinate(-6.2183, 106.8022), "Senayan");

    private Fixtures() {
    }

    public static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    /**
     * Identifiers {@code 00000000-0000-0000-0000-000000000001}, {@code …0002}, … in call order.
     */
    public static IdGenerator sequentialIds() {
        AtomicLong next = new AtomicLong();
        return () -> id(next.incrementAndGet());
    }

    /**
     * The n-th identifier {@link #sequentialIds()} returns.
     */
    public static UUID id(long n) {
        return new UUID(0, n);
    }
}
