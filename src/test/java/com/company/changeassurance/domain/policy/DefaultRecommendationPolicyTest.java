package com.company.changeassurance.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.company.changeassurance.domain.model.EvidenceId;
import com.company.changeassurance.domain.model.Finding;
import com.company.changeassurance.domain.model.FindingCategory;
import com.company.changeassurance.domain.model.FindingId;
import com.company.changeassurance.domain.model.FindingSeverity;
import com.company.changeassurance.domain.model.FindingStatus;
import com.company.changeassurance.domain.model.ReadinessRecommendation;
import com.company.changeassurance.domain.model.RiskAssessment;
import com.company.changeassurance.domain.model.RiskLevel;
import com.company.changeassurance.domain.model.ToolType;

class DefaultRecommendationPolicyTest {

    private final DefaultRecommendationPolicy policy = new DefaultRecommendationPolicy();

    @Test
    void noGoWhenCriticalFindingPresent() {
        Finding critical = new Finding(
                new FindingId("F1"), "SQL-004", "UPDATE without WHERE", "desc",
                FindingSeverity.CRITICAL, FindingCategory.SQL_SAFETY,
                List.of(new EvidenceId("EV-1")), "fix", true, FindingStatus.OPEN, ToolType.ANALYZE_SQL_SCRIPT
        );
        RiskAssessment risk = new RiskAssessment(80, RiskLevel.CRITICAL, "x", 1, 0, 0, 0.8, "v1");
        assertThat(policy.decide(risk, List.of(critical), List.of(), 0.8))
                .isEqualTo(ReadinessRecommendation.NO_GO_RECOMMENDED);
    }

    @Test
    void insufficientWhenCoreArtifactMissing() {
        Finding missing = new Finding(
                new FindingId("F1"), "PKG-002", "Missing deployment plan", "desc",
                FindingSeverity.CRITICAL, FindingCategory.COMPLETENESS,
                List.of(new EvidenceId("EV-1")), "fix", true, FindingStatus.OPEN,
                ToolType.CHECK_CHANGE_PACKAGE_COMPLETENESS
        );
        RiskAssessment risk = new RiskAssessment(10, RiskLevel.MEDIUM, "x", 1, 0, 0, 0.2, "v1");
        assertThat(policy.decide(risk, List.of(missing), List.of(), 0.2))
                .isEqualTo(ReadinessRecommendation.INSUFFICIENT_INFORMATION);
    }

    @Test
    void goWhenClean() {
        RiskAssessment risk = new RiskAssessment(0, RiskLevel.LOW, "clean", 0, 0, 0, 1.0, "v1");
        assertThat(policy.decide(risk, List.of(), List.of(), 1.0))
                .isEqualTo(ReadinessRecommendation.GO);
    }
}
