package com.company.changeassurance.domain.model;

import java.util.Objects;

/**
 * Auditable note describing how AI (or a deterministic stand-in) contributed to a review step.
 * Must not contain hidden chain-of-thought.
 */
public record AiContributionEntry(
        String taskName,
        String purpose,
        String howExecuted,
        String outcome
) {
    public static final String LIVE_MODEL = "LIVE_MODEL";
    public static final String DETERMINISTIC_RULES = "DETERMINISTIC_RULES";
    public static final String TEMPLATE = "TEMPLATE";
    public static final String SKIPPED = "SKIPPED";

    public AiContributionEntry {
        Objects.requireNonNull(taskName, "taskName");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(howExecuted, "howExecuted");
        outcome = outcome == null ? "" : outcome;
    }
}
