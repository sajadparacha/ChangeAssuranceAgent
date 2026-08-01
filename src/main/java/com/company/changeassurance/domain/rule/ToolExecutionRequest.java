package com.company.changeassurance.domain.rule;

import java.util.List;
import java.util.Objects;

import com.company.changeassurance.domain.model.EvidenceId;
import com.company.changeassurance.domain.model.ToolType;
import com.company.changeassurance.domain.rule.ChangeReviewRule.ChangeReviewContext;

public record ToolExecutionRequest(
        String reviewId,
        ToolType toolType,
        List<EvidenceId> inputEvidenceIds,
        String safeReasonSummary,
        ChangeReviewContext context
) {
    public ToolExecutionRequest {
        Objects.requireNonNull(reviewId, "reviewId must not be null");
        Objects.requireNonNull(toolType, "toolType must not be null");
        inputEvidenceIds = List.copyOf(Objects.requireNonNull(inputEvidenceIds, "inputEvidenceIds must not be null"));
        Objects.requireNonNull(safeReasonSummary, "safeReasonSummary must not be null");
        Objects.requireNonNull(context, "context must not be null");
    }
}
