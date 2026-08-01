package com.company.changeassurance.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ToolActivity(
        ActivityId activityId,
        ReviewId reviewId,
        ToolType toolType,
        Instant startTime,
        Instant completionTime,
        ToolActivityStatus status,
        String safeReasonSummary,
        List<EvidenceId> inputEvidenceIds,
        List<EvidenceId> outputEvidenceIds,
        String errorCode,
        String errorDescription
) {

    public ToolActivity {
        Objects.requireNonNull(activityId, "activityId must not be null");
        Objects.requireNonNull(reviewId, "reviewId must not be null");
        Objects.requireNonNull(toolType, "toolType must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(safeReasonSummary, "safeReasonSummary must not be null");
        inputEvidenceIds = List.copyOf(
                Objects.requireNonNull(inputEvidenceIds, "inputEvidenceIds must not be null"));
        outputEvidenceIds = List.copyOf(
                Objects.requireNonNull(outputEvidenceIds, "outputEvidenceIds must not be null"));
    }
}
