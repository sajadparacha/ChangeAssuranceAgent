package com.company.changeassurance.domain.model;

import java.util.Objects;

/**
 * Deterministic risk assessment. Score and level are calculated by policy, not by the LLM.
 */
public record RiskAssessment(
        int numericalScore,
        RiskLevel riskLevel,
        String explanation,
        int criticalFindingCount,
        int highFindingCount,
        int missingInformationCount,
        double evidenceCoverageScore,
        String ruleVersion
) {

    public RiskAssessment {
        if (numericalScore < 0) {
            throw new IllegalArgumentException("numericalScore must be >= 0");
        }
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
        if (criticalFindingCount < 0 || highFindingCount < 0 || missingInformationCount < 0) {
            throw new IllegalArgumentException("finding counts must be >= 0");
        }
        if (evidenceCoverageScore < 0.0d || evidenceCoverageScore > 1.0d) {
            throw new IllegalArgumentException("evidenceCoverageScore must be between 0.0 and 1.0");
        }
        Objects.requireNonNull(ruleVersion, "ruleVersion must not be null");
    }
}
