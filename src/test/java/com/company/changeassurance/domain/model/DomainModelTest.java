package com.company.changeassurance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class DomainModelTest {

    @Test
    void reviewStageContainsAllControlledWorkflowStages() {
        assertThat(EnumSet.allOf(ReviewStage.class)).containsExactlyInAnyOrder(
                ReviewStage.RECEIVED,
                ReviewStage.VALIDATING_INPUT,
                ReviewStage.CREATING_EVIDENCE,
                ReviewStage.CLASSIFYING_CHANGE,
                ReviewStage.PLANNING_REVIEW,
                ReviewStage.EXECUTING_TOOLS,
                ReviewStage.ANALYZING_EVIDENCE,
                ReviewStage.WAITING_FOR_INFORMATION,
                ReviewStage.GENERATING_RISK_SCENARIOS,
                ReviewStage.GENERATING_DRAFT_REPORT,
                ReviewStage.CRITIC_REVIEW,
                ReviewStage.CALCULATING_RECOMMENDATION,
                ReviewStage.COMPLETED,
                ReviewStage.FAILED
        );
    }

    @Test
    void changeReviewTracksStageTransitions() {
        Instant created = Instant.parse("2026-08-01T00:00:00Z");
        ChangeReview review = new ChangeReview(
                new ReviewId("REV-2026-00001"),
                "billing-service",
                "Package body tune",
                "Minor performance improvement",
                "PROD",
                "2026-08-02T02:00/04:00",
                ChangeType.PLSQL,
                created
        );

        Instant next = Instant.parse("2026-08-01T00:00:01Z");
        review.transitionTo(
                ReviewStage.VALIDATING_INPUT,
                "Request accepted",
                ActorType.SYSTEM,
                next,
                List.of(),
                List.of()
        );

        assertThat(review.getCurrentStage()).isEqualTo(ReviewStage.VALIDATING_INPUT);
        assertThat(review.getReviewStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getStageTransitions()).hasSize(1);
        assertThat(review.getStageTransitions().get(0).previousStage()).isEqualTo(ReviewStage.RECEIVED);
        assertThat(review.getStageTransitions().get(0).actorType()).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    void changeClassificationRejectsOutOfRangeConfidence() {
        assertThatThrownBy(() -> new ChangeClassification(
                ChangeType.MIXED,
                List.of(ChangeType.PLSQL),
                Complexity.HIGH,
                1.5d,
                List.of(ReviewCapability.SQL_SAFETY),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void riskScenarioMustBeHypothesis() {
        assertThatThrownBy(() -> new RiskScenario(
                new ScenarioId("RS-1"),
                "Broad update impact",
                "Update without WHERE may affect all rows",
                List.of("Deploy", "Update runs", "Data corruption"),
                List.of(new EvidenceId("EV-1")),
                List.of(),
                0.7d,
                "Validate row counts before deploy",
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hypothes");
    }

    @Test
    void readinessRecommendationValuesMatchPolicyContract() {
        assertThat(Arrays.stream(ReadinessRecommendation.values()).map(Enum::name))
                .containsExactlyInAnyOrder(
                        "GO",
                        "CONDITIONAL_GO",
                        "NO_GO_RECOMMENDED",
                        "INSUFFICIENT_INFORMATION"
                );
    }

    @Test
    void aiAssessmentUnavailableFactory() {
        AiAssessment assessment = AiAssessment.unavailable();
        assertThat(assessment.availabilityStatus()).isEqualTo(AiAssessment.UNAVAILABLE);
        assertThat(assessment.citedEvidenceIds()).isEmpty();
    }
}
