package com.company.changeassurance.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.company.changeassurance.adapter.out.db.FakeDatabaseMetadataAdapter;
import com.company.changeassurance.configuration.ChangeAssuranceProperties;
import com.company.changeassurance.domain.db.PackageDbImpact;
import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.ChangeType;
import com.company.changeassurance.domain.model.ReviewId;
import com.company.changeassurance.domain.model.ToolType;
import com.company.changeassurance.domain.policy.PackageImpactAssessmentCalculator;

class InvestigationLoopServiceTest {

    private final InvestigationLoopService loop = new InvestigationLoopService(
            new ChangeAssuranceProperties(null, null, null, null));

    @Test
    void suggestsTransitiveAndSourceWhenBlastRadiusLarge() {
        FakeDatabaseMetadataAdapter catalog = new FakeDatabaseMetadataAdapter("APP");
        PackageDbImpact impact = catalog.analyzeImpact("APP", "BILLING_PKG", 3, 50, 10);
        // Clear follow-up results so heuristics decide from direct dependents only
        impact = PackageDbImpact.of(
                impact.catalogMode(),
                impact.packageInfo(),
                impact.procedures(),
                impact.dependencies(),
                impact.dependents(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );

        ChangeReview review = new ChangeReview(
                new ReviewId("REV-L1"),
                "database",
                "impact",
                null,
                null,
                null,
                ChangeType.PLSQL,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        review.setPackageTarget("BILLING_PKG", "APP");
        review.setPackageDbImpact(impact);

        List<ToolType> suggested = loop.suggestFollowUpTools(review, EnumSet.of(
                ToolType.GET_PACKAGE_OBJECT_INFO,
                ToolType.ANALYZE_DIRECT_DEPENDENTS,
                ToolType.ANALYZE_PACKAGE_DEPENDENCIES,
                ToolType.ANALYZE_SCHEDULER_JOBS,
                ToolType.ANALYZE_RELATED_OBJECT_HEALTH
        ));

        assertThat(suggested).contains(
                ToolType.ANALYZE_TRANSITIVE_DEPENDENTS,
                ToolType.ANALYZE_SOURCE_REFERENCES);
    }

    @Test
    void calculatorMarksBillingHighOrMedium() {
        PackageDbImpact impact = new FakeDatabaseMetadataAdapter("APP").analyzeImpact("APP", "BILLING_PKG");
        var assessment = PackageImpactAssessmentCalculator.defaults().assess(impact);
        assertThat(assessment.overallImpact().name()).isIn("MEDIUM", "HIGH");
        assertThat(assessment.recommendedTesting()).isNotEmpty();
        assertThat(assessment.why()).isNotEmpty();
    }
}
