package com.company.changeassurance.domain.model;

import java.util.List;
import java.util.Objects;

public record ReviewPlanStep(
        PlanStepId stepId,
        int sequence,
        ReviewCapability capability,
        ToolType toolType,
        String purpose,
        List<String> preconditions,
        PlanStepStatus status,
        List<EvidenceId> evidenceIds
) {

    public ReviewPlanStep {
        Objects.requireNonNull(stepId, "stepId must not be null");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be >= 1");
        }
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(toolType, "toolType must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        preconditions = List.copyOf(Objects.requireNonNull(preconditions, "preconditions must not be null"));
        Objects.requireNonNull(status, "status must not be null");
        evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds must not be null"));
    }
}
