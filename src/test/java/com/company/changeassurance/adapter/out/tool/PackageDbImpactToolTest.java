package com.company.changeassurance.adapter.out.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.company.changeassurance.adapter.out.db.FakeDatabaseMetadataAdapter;
import com.company.changeassurance.application.service.IdGenerator;
import com.company.changeassurance.configuration.ChangeAssuranceProperties;
import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.ChangeType;
import com.company.changeassurance.domain.model.ReviewId;
import com.company.changeassurance.domain.model.ToolType;
import com.company.changeassurance.domain.rule.ChangeReviewRule.ChangeReviewContext;
import com.company.changeassurance.domain.rule.ToolExecutionRequest;
import com.company.changeassurance.domain.rule.ToolExecutionResult;

class PackageDbImpactToolTest {

    private final ChangeAssuranceProperties properties =
            new ChangeAssuranceProperties(null, null, null, null);

    private final PackageDbImpactTool tool = new PackageDbImpactTool(
            new IdGenerator(),
            new FakeDatabaseMetadataAdapter("APP"),
            properties
    );

    @Test
    void findsImpactForBillingPackage() {
        ChangeReview review = new ChangeReview(
                new ReviewId("REV-1"),
                "database",
                "DB impact: BILLING_PKG",
                null,
                null,
                null,
                ChangeType.PLSQL,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        review.setPackageTarget("BILLING_PKG", "APP");

        ToolExecutionResult result = tool.execute(new ToolExecutionRequest(
                "REV-1",
                ToolType.ANALYZE_PACKAGE_DB_IMPACT,
                List.of(),
                "test",
                ChangeReviewContext.of(review)
        ));

        assertThat(result.status().name()).isEqualTo("SUCCEEDED");
        assertThat(review.getPackageDbImpact()).isNotNull();
        assertThat(review.getPackageDbImpact().packageFound()).isTrue();
        assertThat(review.getAffectedObjects()).isNotEmpty();
        assertThat(result.findings()).extracting(f -> f.ruleCode())
                .contains("DBI-004", "DBI-005", "DBI-009");
        assertThat(review.getPackageDbImpact().dependents()).hasSize(4);
        assertThat(review.getPackageDbImpact().schedulerJobs()).isNotEmpty();
    }

    @Test
    void emitsNotFoundFinding() {
        ChangeReview review = new ChangeReview(
                new ReviewId("REV-2"),
                "database",
                "DB impact: NOPE",
                null,
                null,
                null,
                ChangeType.PLSQL,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        review.setPackageTarget("NOPE_PKG", "APP");

        ToolExecutionResult result = tool.execute(new ToolExecutionRequest(
                "REV-2",
                ToolType.ANALYZE_PACKAGE_DB_IMPACT,
                List.of(),
                "test",
                ChangeReviewContext.of(review)
        ));

        assertThat(result.findings()).extracting(f -> f.ruleCode()).containsExactly("DBI-001");
        assertThat(review.getPackageDbImpact().packageFound()).isFalse();
    }
}
