package com.company.changeassurance.adapter.out.clock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class FixedClockTest {

    @Test
    void returnsConfiguredInstantAndAllowsAdvance() {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        FixedClock clock = new FixedClock(start);

        assertThat(clock.now()).isEqualTo(start);

        Instant later = Instant.parse("2026-08-01T01:00:00Z");
        clock.setInstant(later);
        assertThat(clock.now()).isEqualTo(later);
    }
}
