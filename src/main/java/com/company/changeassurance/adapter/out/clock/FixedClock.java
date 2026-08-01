package com.company.changeassurance.adapter.out.clock;

import java.time.Instant;
import java.util.Objects;

import com.company.changeassurance.application.port.out.ClockPort;

/**
 * Fixed clock for deterministic unit and integration tests.
 */
public final class FixedClock implements ClockPort {

    private Instant instant;

    public FixedClock(Instant instant) {
        this.instant = Objects.requireNonNull(instant, "instant must not be null");
    }

    @Override
    public Instant now() {
        return instant;
    }

    public void setInstant(Instant instant) {
        this.instant = Objects.requireNonNull(instant, "instant must not be null");
    }
}
