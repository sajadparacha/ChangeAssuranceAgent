package com.company.changeassurance.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.company.changeassurance.domain.db.DbObjectRef;
import com.company.changeassurance.domain.db.DbPackageInfo;
import com.company.changeassurance.domain.db.DbPackageProcedure;
import com.company.changeassurance.domain.db.ImpactLevel;
import com.company.changeassurance.domain.db.PackageDbImpact;
import com.company.changeassurance.domain.db.PackageImpactAssessment;
import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.ChangeType;
import com.company.changeassurance.domain.model.ReviewId;
import com.company.changeassurance.domain.policy.PackageImpactAssessmentCalculator;

class ReportAssemblerDatabaseImpactTest {

    private final ReportAssembler assembler = new ReportAssembler();

    @Test
    void includesDatabaseImpactSectionAndFakeCatalogLimitation() {
        ChangeReview review = new ChangeReview(
                new ReviewId("REV-R1"),
                "APP",
                "DB impact: BILLING_PKG",
                null,
                null,
                null,
                ChangeType.PLSQL,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        review.setPackageTarget("BILLING_PKG", "APP");
        PackageDbImpact impactData = PackageDbImpact.of(
                "fake",
                new DbPackageInfo("APP", "BILLING_PKG", true, true, "VALID"),
                List.of(new DbPackageProcedure("REFRESH_ACCOUNTS", "PROCEDURE")),
                List.of(new DbObjectRef("APP", "BILLING_ACCOUNTS", "TABLE", "VALID")),
                List.of(
                        new DbObjectRef("APP", "V_BILLING_STATUS", "VIEW", "VALID"),
                        new DbObjectRef("APP", "API_BILLING_WRAPPER", "PACKAGE", "VALID"),
                        new DbObjectRef("APP", "BILLING_REFRESH_JOB", "JOB", "VALID"),
                        new DbObjectRef("APP", "TRG_BILLING", "TRIGGER", "VALID")
                )
        );
        PackageImpactAssessment assessment = PackageImpactAssessmentCalculator.defaults().assess(impactData);
        review.setPackageDbImpact(impactData.withImpactAssessment(assessment));

        Map<String, Object> report = assembler.assemble(review);
        assertThat(report).containsKey("databaseImpact");
        @SuppressWarnings("unchecked")
        Map<String, Object> impact = (Map<String, Object>) report.get("databaseImpact");
        assertThat(impact.get("available")).isEqualTo(true);
        assertThat(impact.get("catalogMode")).isEqualTo("fake");
        assertThat(impact.get("packageFound")).isEqualTo(true);
        assertThat(impact.get("overallImpact")).isIn("MEDIUM", "HIGH", ImpactLevel.MEDIUM.name(), ImpactLevel.HIGH.name());
        assertThat((List<?>) impact.get("why")).isNotEmpty();
        assertThat((List<?>) impact.get("recommendedTesting")).isNotEmpty();
        @SuppressWarnings("unchecked")
        List<String> limitations = (List<String>) report.get("limitations");
        assertThat(limitations).anyMatch(l -> l.contains("fake"));
        assertThat(limitations).anyMatch(l -> l.contains("Application source code was not scanned"));
        assertThat(limitations).anyMatch(l -> l.contains("cannot be overridden by AI"));
        assertThat(impact).containsKey("deployChange");
    }
}
