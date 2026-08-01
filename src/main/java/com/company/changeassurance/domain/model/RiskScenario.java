package com.company.changeassurance.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Operational risk hypothesis. Always treated as a hypothesis, never as an observed fact.
 */
public record RiskScenario(
        ScenarioId scenarioId,
        String title,
        String description,
        List<String> eventSequence,
        List<EvidenceId> supportingEvidenceIds,
        List<EvidenceId> contradictingEvidenceIds,
        double confidence,
        String requiredValidation,
        boolean hypothesis
) {

    public RiskScenario {
        Objects.requireNonNull(scenarioId, "scenarioId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
        eventSequence = List.copyOf(Objects.requireNonNull(eventSequence, "eventSequence must not be null"));
        supportingEvidenceIds = List.copyOf(
                Objects.requireNonNull(supportingEvidenceIds, "supportingEvidenceIds must not be null"));
        contradictingEvidenceIds = List.copyOf(
                Objects.requireNonNull(contradictingEvidenceIds, "contradictingEvidenceIds must not be null"));
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0 inclusive");
        }
        Objects.requireNonNull(requiredValidation, "requiredValidation must not be null");
        if (!hypothesis) {
            throw new IllegalArgumentException("risk scenarios must be marked as hypotheses");
        }
    }
}
