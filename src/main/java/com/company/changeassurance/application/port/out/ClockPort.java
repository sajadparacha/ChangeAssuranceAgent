package com.company.changeassurance.application.port.out;

import java.time.Instant;

/**
 * Abstraction over the system clock for deterministic tests and audit timestamps.
 */
public interface ClockPort {

    Instant now();
}
