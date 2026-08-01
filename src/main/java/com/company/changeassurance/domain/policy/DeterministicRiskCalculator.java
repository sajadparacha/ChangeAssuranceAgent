package com.company.changeassurance.domain.policy;

import java.util.List;

import com.company.changeassurance.domain.model.Finding;
import com.company.changeassurance.domain.model.FindingSeverity;
import com.company.changeassurance.domain.model.FindingStatus;
import com.company.changeassurance.domain.model.InformationGap;
import com.company.changeassurance.domain.model.InformationGapResolutionStatus;
import com.company.changeassurance.domain.model.RiskAssessment;
import com.company.changeassurance.domain.model.RiskLevel;

public final class DeterministicRiskCalculator {

    private final RiskScoringConfig config;

    public DeterministicRiskCalculator(RiskScoringConfig config) {
        this.config = config == null ? RiskScoringConfig.defaults() : config;
    }

    public RiskAssessment calculate(
            List<Finding> findings,
            List<InformationGap> gaps,
            double evidenceCoverageScore,
            boolean rollbackGapsPresent,
            boolean unsupportedSql
    ) {
        int critical = count(findings, FindingSeverity.CRITICAL);
        int high = count(findings, FindingSeverity.HIGH);
        int medium = count(findings, FindingSeverity.MEDIUM);
        int openGaps = (int) gaps.stream()
                .filter(g -> g.resolutionStatus() == InformationGapResolutionStatus.OPEN
                        || g.resolutionStatus() == InformationGapResolutionStatus.UNRESOLVED)
                .count();

        int score = critical * config.criticalPenalty()
                + high * config.highPenalty()
                + medium * config.mediumPenalty()
                + openGaps * config.missingInfoPenalty();
        if (rollbackGapsPresent) {
            score += config.rollbackGapPenalty();
        }
        if (unsupportedSql) {
            score += config.unsupportedSqlPenalty();
        }
        if (evidenceCoverageScore < config.evidenceCoverageThreshold()) {
            score += config.missingInfoPenalty();
        }

        RiskLevel level;
        if (critical > 0 || score >= config.noGoScoreThreshold()) {
            level = RiskLevel.CRITICAL;
        } else if (high > 0 || score >= config.conditionalScoreThreshold()) {
            level = RiskLevel.HIGH;
        } else if (medium > 0 || score > 0) {
            level = RiskLevel.MEDIUM;
        } else {
            level = RiskLevel.LOW;
        }

        String explanation = "Deterministic score=" + score
                + " (critical=" + critical + ", high=" + high
                + ", missingInfo=" + openGaps
                + ", evidenceCoverage=" + String.format("%.2f", evidenceCoverageScore) + ")";

        return new RiskAssessment(
                score,
                level,
                explanation,
                critical,
                high,
                openGaps,
                evidenceCoverageScore,
                config.ruleVersion()
        );
    }

    private static int count(List<Finding> findings, FindingSeverity severity) {
        return (int) findings.stream()
                .filter(f -> f.status() == FindingStatus.OPEN)
                .filter(f -> f.severity() == severity)
                .count();
    }

    public RiskScoringConfig config() {
        return config;
    }
}
