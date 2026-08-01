package com.company.changeassurance.domain.model;

import java.util.List;
import java.util.Objects;

public record InformationGap(
        GapId gapId,
        String description,
        String reasonRequired,
        FindingSeverity severity,
        List<EvidenceId> relatedEvidenceIds,
        String suggestedQuestion,
        InformationGapResolutionStatus resolutionStatus,
        String userAnswer
) {

    public InformationGap {
        Objects.requireNonNull(gapId, "gapId must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(reasonRequired, "reasonRequired must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        relatedEvidenceIds = List.copyOf(
                Objects.requireNonNull(relatedEvidenceIds, "relatedEvidenceIds must not be null"));
        Objects.requireNonNull(resolutionStatus, "resolutionStatus must not be null");
    }
}
