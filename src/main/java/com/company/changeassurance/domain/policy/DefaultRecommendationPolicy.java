package com.company.changeassurance.domain.policy;

import java.util.List;

import com.company.changeassurance.domain.model.Finding;
import com.company.changeassurance.domain.model.FindingCategory;
import com.company.changeassurance.domain.model.FindingSeverity;
import com.company.changeassurance.domain.model.FindingStatus;
import com.company.changeassurance.domain.model.InformationGap;
import com.company.changeassurance.domain.model.InformationGapResolutionStatus;
import com.company.changeassurance.domain.model.ReadinessRecommendation;
import com.company.changeassurance.domain.model.RiskAssessment;
import com.company.changeassurance.domain.model.RiskLevel;

public final class DefaultRecommendationPolicy implements RecommendationPolicy {

    private final RiskScoringConfig config;

    public DefaultRecommendationPolicy(RiskScoringConfig config) {
        this.config = config == null ? RiskScoringConfig.defaults() : config;
    }

    public DefaultRecommendationPolicy() {
        this(RiskScoringConfig.defaults());
    }

    @Override
    public ReadinessRecommendation decide(
            RiskAssessment riskAssessment,
            List<Finding> findings,
            List<InformationGap> informationGaps,
            double evidenceCoverageScore
    ) {
        boolean unresolvedCritical = findings.stream()
                .anyMatch(f -> f.status() == FindingStatus.OPEN && f.severity() == FindingSeverity.CRITICAL);
        boolean unresolvedHigh = findings.stream()
                .anyMatch(f -> f.status() == FindingStatus.OPEN && f.severity() == FindingSeverity.HIGH);
        boolean openGaps = informationGaps.stream()
                .anyMatch(g -> g.resolutionStatus() == InformationGapResolutionStatus.OPEN
                        || g.resolutionStatus() == InformationGapResolutionStatus.UNRESOLVED);
        boolean credential = findings.stream()
                .anyMatch(f -> "SQL-007".equals(f.ruleCode()) && f.status() == FindingStatus.OPEN);
        boolean envConflict = findings.stream()
                .anyMatch(f -> ("CON-002".equals(f.ruleCode()) || "RBK-003".equals(f.ruleCode()))
                        && f.status() == FindingStatus.OPEN);
        boolean broadDml = findings.stream()
                .anyMatch(f -> ("SQL-003".equals(f.ruleCode()) || "SQL-004".equals(f.ruleCode()))
                        && f.status() == FindingStatus.OPEN);
        boolean missingCoreArtifact = findings.stream()
                .anyMatch(f -> f.category() == FindingCategory.COMPLETENESS
                        && f.severity().ordinal() >= FindingSeverity.HIGH.ordinal()
                        && f.status() == FindingStatus.OPEN
                        && (f.ruleCode().startsWith("PKG-001")
                        || f.ruleCode().startsWith("PKG-002")
                        || f.ruleCode().startsWith("PKG-003")
                        || f.ruleCode().startsWith("PKG-004")));
        boolean unsupportedSql = findings.stream()
                .anyMatch(f -> "SQL-009".equals(f.ruleCode()) && f.status() == FindingStatus.OPEN);

        if (missingCoreArtifact
                || evidenceCoverageScore < config.evidenceCoverageThreshold()
                || (openGaps && unresolvedHigh)
                || (unsupportedSql && evidenceCoverageScore < 0.75d)) {
            return ReadinessRecommendation.INSUFFICIENT_INFORMATION;
        }

        if (unresolvedCritical
                || credential
                || envConflict
                || (broadDml && findings.stream().anyMatch(f -> f.ruleCode().startsWith("RBK-") && f.status() == FindingStatus.OPEN))
                || riskAssessment.riskLevel() == RiskLevel.CRITICAL
                || riskAssessment.numericalScore() >= config.noGoScoreThreshold()) {
            return ReadinessRecommendation.NO_GO_RECOMMENDED;
        }

        if (unresolvedHigh
                || riskAssessment.riskLevel() == RiskLevel.HIGH
                || riskAssessment.numericalScore() >= config.conditionalScoreThreshold()
                || openGaps) {
            return ReadinessRecommendation.CONDITIONAL_GO;
        }

        boolean anyMedium = findings.stream()
                .anyMatch(f -> f.status() == FindingStatus.OPEN && f.severity() == FindingSeverity.MEDIUM);
        if (anyMedium) {
            return ReadinessRecommendation.CONDITIONAL_GO;
        }

        return ReadinessRecommendation.GO;
    }
}
