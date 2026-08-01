package com.company.changeassurance.domain.rule;

import java.util.List;
import java.util.Objects;

import com.company.changeassurance.domain.model.Evidence;
import com.company.changeassurance.domain.model.Finding;
import com.company.changeassurance.domain.model.ToolActivityStatus;

public record ToolExecutionResult(
        ToolActivityStatus status,
        List<Finding> findings,
        List<Evidence> outputEvidence,
        String safeSummary,
        String errorCode,
        String errorDescription
) {

    public ToolExecutionResult {
        Objects.requireNonNull(status, "status must not be null");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings must not be null"));
        outputEvidence = List.copyOf(Objects.requireNonNull(outputEvidence, "outputEvidence must not be null"));
        Objects.requireNonNull(safeSummary, "safeSummary must not be null");
    }

    public static ToolExecutionResult success(
            List<Finding> findings,
            List<Evidence> outputEvidence,
            String safeSummary
    ) {
        return new ToolExecutionResult(
                ToolActivityStatus.SUCCEEDED,
                findings,
                outputEvidence,
                safeSummary,
                null,
                null
        );
    }

    public static ToolExecutionResult failure(String errorCode, String errorDescription) {
        return new ToolExecutionResult(
                ToolActivityStatus.FAILED,
                List.of(),
                List.of(),
                "Tool execution failed",
                errorCode,
                errorDescription
        );
    }
}
