package com.company.changeassurance.domain.model;

import java.util.List;
import java.util.Objects;

public record Finding(
        FindingId findingId,
        String ruleCode,
        String title,
        String description,
        FindingSeverity severity,
        FindingCategory category,
        List<EvidenceId> evidenceIds,
        String requiredAction,
        boolean deterministic,
        FindingStatus status,
        ToolType sourceTool
) {

    public Finding {
        Objects.requireNonNull(findingId, "findingId must not be null");
        Objects.requireNonNull(ruleCode, "ruleCode must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(category, "category must not be null");
        evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds must not be null"));
        Objects.requireNonNull(status, "status must not be null");
    }
}
