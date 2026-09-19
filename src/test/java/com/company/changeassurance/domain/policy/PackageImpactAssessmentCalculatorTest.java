package com.company.changeassurance.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.company.changeassurance.domain.db.DbObjectRef;
import com.company.changeassurance.domain.db.DbPackageInfo;
import com.company.changeassurance.domain.db.ImpactLevel;
import com.company.changeassurance.domain.db.PackageDbImpact;
import com.company.changeassurance.domain.db.PackageImpactAssessment;
import com.company.changeassurance.domain.sql.PackageDeployDelta;

class PackageImpactAssessmentCalculatorTest {

    private final PackageImpactAssessmentCalculator calculator = PackageImpactAssessmentCalculator.defaults();

    @Test
    void bodyOnlyKeepsLowerImpactWhenBlastRadiusSmall() {
        PackageDbImpact impact = PackageDbImpact.of(
                "fake",
                new DbPackageInfo("APP", "DEMO_PKG", true, true, "VALID"),
                List.of(),
                List.of(),
                List.of(new DbObjectRef("APP", "V_DEMO", "VIEW", "VALID"))
        );
        PackageDeployDelta bodyOnly = new PackageDeployDelta.Builder()
                .inferredFromScript(true)
                .touch("DEMO_PKG", "APP", false, true)
                .build()
                .focusedOn("DEMO_PKG");

        PackageImpactAssessment assessment = calculator.assess(impact, bodyOnly);
        assertThat(assessment.overallImpact()).isIn(ImpactLevel.LOW, ImpactLevel.MEDIUM);
        assertThat(assessment.why()).anyMatch(w -> w.toLowerCase().contains("body only"));
        assertThat(assessment.recommendedTesting()).anyMatch(t -> t.toLowerCase().contains("body-only"));
    }

    @Test
    void specChangeRaisesImpactAndApiTesting() {
        PackageDbImpact impact = PackageDbImpact.of(
                "fake",
                new DbPackageInfo("APP", "BILLING_PKG", true, true, "VALID"),
                List.of(),
                List.of(),
                List.of(
                        new DbObjectRef("APP", "V1", "VIEW", "VALID"),
                        new DbObjectRef("APP", "V2", "VIEW", "VALID")
                )
        );
        PackageDeployDelta spec = new PackageDeployDelta.Builder()
                .inferredFromScript(true)
                .touch("BILLING_PKG", "APP", true, false)
                .build()
                .focusedOn("BILLING_PKG");

        PackageImpactAssessment assessment = calculator.assess(impact, spec);
        assertThat(assessment.overallImpact()).isIn(ImpactLevel.MEDIUM, ImpactLevel.HIGH);
        assertThat(assessment.why()).anyMatch(w -> w.toLowerCase().contains("specification"));
        assertThat(assessment.recommendedTesting()).anyMatch(t -> t.toLowerCase().contains("entry points"));
    }
}
