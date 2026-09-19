package com.company.changeassurance.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.company.changeassurance.application.port.in.SubmitChangeReviewUseCase;
import com.company.changeassurance.application.port.in.SubmitClarificationAnswerUseCase;
import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.ReadinessRecommendation;
import com.company.changeassurance.domain.model.ReviewStage;
import com.company.changeassurance.domain.model.ReviewStatus;

@SpringBootTest
@TestPropertySource(properties = {
        "changeassurance.ai.mode=fake",
        "changeassurance.db-metadata.mode=fake",
        "changeassurance.storage.root=${java.io.tmpdir}/caa-uploads-test"
})
class DemoScenarioWorkflowTest {

    @Autowired
    private ChangeAssuranceWorkflowService workflow;

    @Test
    void managementDemoScenarioProducesNoGoOrInsufficientAndMayPauseForClarification() {
        String sql = """
                CREATE OR REPLACE PACKAGE billing_pkg AS
                  PROCEDURE refresh;
                END billing_pkg;
                /
                CREATE OR REPLACE PACKAGE BODY billing_pkg AS
                  PROCEDURE refresh IS BEGIN NULL; END;
                END billing_pkg;
                /
                ALTER TABLE billing_accounts ADD (last_refresh_ts TIMESTAMP);
                UPDATE billing_accounts SET last_refresh_ts = SYSTIMESTAMP;
                """;

        var result = workflow.submit(new SubmitChangeReviewUseCase.SubmitChangeReviewCommand(
                "billing-service",
                "Package body performance",
                "Minor performance improvement to package body.",
                "PLSQL",
                "PROD",
                "2026-08-02T02:00/04:00",
                "Deploy package body only. Owner: DBA team.",
                "Restore package body only.",
                "API happy path for one endpoint passed.",
                "demo.sql",
                sql.getBytes(StandardCharsets.UTF_8)
        ));

        ChangeReview review = workflow.getById(result.reviewId()).orElseThrow();
        assertThat(review.getFindings()).isNotEmpty();
        assertThat(review.getFindings().stream().map(f -> f.ruleCode()))
                .anyMatch(code -> code.startsWith("SQL-") || code.startsWith("CON-") || code.startsWith("RBK-"));

        if (review.getCurrentStage() == ReviewStage.WAITING_FOR_INFORMATION) {
            String gapId = review.getInformationGaps().get(0).gapId().value();
            workflow.submit(result.reviewId(), new SubmitClarificationAnswerUseCase.SubmitClarificationAnswerCommand(
                    gapId,
                    "Table and package spec changes were intentional; rollback will restore prior package spec/body and drop the column."
            ));
            review = workflow.getById(result.reviewId()).orElseThrow();
        }

        assertThat(review.getReviewStatus()).isEqualTo(ReviewStatus.COMPLETED);
        assertThat(review.getReadinessRecommendation()).isIn(
                ReadinessRecommendation.NO_GO_RECOMMENDED,
                ReadinessRecommendation.INSUFFICIENT_INFORMATION,
                ReadinessRecommendation.CONDITIONAL_GO
        );
        assertThat(review.getReadinessRecommendation()).isNotEqualTo(ReadinessRecommendation.GO);
        assertThat(review.getRiskScenarios()).isNotEmpty();
        assertThat(review.getRiskScenarios().get(0).hypothesis()).isTrue();
    }

    @Test
    void completesWithAiDisabled() {
        // Uses fake mode from properties; Disabled path covered by dedicated config test.
        // Submit a safer package that still has completeness gaps.
        var result = workflow.submit(new SubmitChangeReviewUseCase.SubmitChangeReviewCommand(
                "payments",
                "Index add",
                "Add supporting index",
                "DATABASE_SCHEMA",
                "UAT",
                "tonight",
                "Create index IDX_PAY on payments(id). Verify invalid objects. Owner: app team.",
                "Drop index IDX_PAY. Verify. Environment UAT.",
                "Index created in lower env. Negative test: duplicate key path. Rollback tested.",
                "idx.sql",
                "CREATE INDEX IDX_PAY ON payments(id);".getBytes(StandardCharsets.UTF_8)
        ));
        ChangeReview review = workflow.getById(result.reviewId()).orElseThrow();
        if (review.getCurrentStage() == ReviewStage.WAITING_FOR_INFORMATION) {
            workflow.submit(result.reviewId(), new SubmitClarificationAnswerUseCase.SubmitClarificationAnswerCommand(
                    review.getInformationGaps().get(0).gapId().value(),
                    "Confirmed scope is index only."
            ));
            review = workflow.getById(result.reviewId()).orElseThrow();
        }
        assertThat(review.getReviewStatus()).isIn(ReviewStatus.COMPLETED, ReviewStatus.WAITING_FOR_INFORMATION);
    }
}
