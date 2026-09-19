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

class DiscretePackageImpactToolsTest {

    private final IdGenerator ids = new IdGenerator();
    private final FakeDatabaseMetadataAdapter catalog = new FakeDatabaseMetadataAdapter("APP");
    private final ChangeAssuranceProperties properties =
            new ChangeAssuranceProperties(null, null, null, null);

    @Test
    void discreteToolsAccumulateImpactEvidence() {
        ChangeReview review = new ChangeReview(
                new ReviewId("REV-D1"),
                "database",
                "DB impact: BILLING_PKG",
                null,
                null,
                null,
                ChangeType.PLSQL,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        review.setPackageTarget("BILLING_PKG", "APP");
        ChangeReviewContext ctx = ChangeReviewContext.of(review);

        new GetPackageObjectInfoTool(ids, catalog).execute(new ToolExecutionRequest(
                "REV-D1", ToolType.GET_PACKAGE_OBJECT_INFO, List.of(), "t", ctx));
        new AnalyzeDirectDependentsTool(ids, catalog, properties).execute(new ToolExecutionRequest(
                "REV-D1", ToolType.ANALYZE_DIRECT_DEPENDENTS, List.of(), "t", ctx));
        new AnalyzePackageDependenciesTool(ids, catalog).execute(new ToolExecutionRequest(
                "REV-D1", ToolType.ANALYZE_PACKAGE_DEPENDENCIES, List.of(), "t", ctx));
        new AnalyzeSchedulerJobsTool(ids, catalog).execute(new ToolExecutionRequest(
                "REV-D1", ToolType.ANALYZE_SCHEDULER_JOBS, List.of(), "t", ctx));
        new AnalyzeRelatedObjectHealthTool(ids, catalog).execute(new ToolExecutionRequest(
                "REV-D1", ToolType.ANALYZE_RELATED_OBJECT_HEALTH, List.of(), "t", ctx));
        new AnalyzeTransitiveDependentsTool(ids, catalog, properties).execute(new ToolExecutionRequest(
                "REV-D1", ToolType.ANALYZE_TRANSITIVE_DEPENDENTS, List.of(), "t", ctx));
        new AnalyzeSourceReferencesTool(ids, catalog, properties).execute(new ToolExecutionRequest(
                "REV-D1", ToolType.ANALYZE_SOURCE_REFERENCES, List.of(), "t", ctx));

        assertThat(review.getPackageDbImpact()).isNotNull();
        assertThat(review.getPackageDbImpact().packageFound()).isTrue();
        assertThat(review.getPackageDbImpact().procedures()).isNotEmpty();
        assertThat(review.getPackageDbImpact().dependents()).isNotEmpty();
        assertThat(review.getPackageDbImpact().dependencies()).isNotEmpty();
        assertThat(review.getPackageDbImpact().schedulerJobs()).isNotEmpty();
        assertThat(review.getPackageDbImpact().transitiveDependents()).isNotEmpty();
        assertThat(review.getPackageDbImpact().sourceReferences()).isNotEmpty();
        assertThat(review.getPackageDbImpact().invalidRelatedObjects()).isNotEmpty();
    }
}
