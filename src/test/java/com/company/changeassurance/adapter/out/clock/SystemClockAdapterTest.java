package com.company.changeassurance.adapter.out.clock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class SystemClockAdapterTest {

    @Test
    void delegatesToInjectedClock() {
        Instant fixed = Instant.parse("2026-01-15T12:00:00Z");
        SystemClockAdapter adapter = new SystemClockAdapter(Clock.fixed(fixed, ZoneOffset.UTC));

        assertThat(adapter.now()).isEqualTo(fixed);
    }
}
