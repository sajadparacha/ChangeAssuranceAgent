package com.company.changeassurance.domain.policy;

public record RiskScoringConfig(
        int criticalPenalty,
        int highPenalty,
        int mediumPenalty,
        int missingInfoPenalty,
        int rollbackGapPenalty,
        int unsupportedSqlPenalty,
        double evidenceCoverageThreshold,
        int noGoScoreThreshold,
        int conditionalScoreThreshold,
        String ruleVersion
) {
    public static RiskScoringConfig defaults() {
        return new RiskScoringConfig(
                40, 20, 8, 15, 25, 10,
                0.6d,
                70,
                35,
                "risk-policy-v1"
        );
    }
}
