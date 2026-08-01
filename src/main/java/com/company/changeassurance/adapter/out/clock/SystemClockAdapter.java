package com.company.changeassurance.adapter.out.clock;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.company.changeassurance.application.port.out.ClockPort;

/**
 * Production clock adapter backed by {@link java.time.Clock}.
 */
public final class SystemClockAdapter implements ClockPort {

    private final Clock clock;

    public SystemClockAdapter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public SystemClockAdapter() {
        this(Clock.systemUTC());
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
