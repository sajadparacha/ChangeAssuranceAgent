package com.company.changeassurance.domain.policy;

import java.util.ArrayList;
import java.util.List;

import com.company.changeassurance.domain.db.ImpactLevel;
import com.company.changeassurance.domain.db.PackageDbImpact;
import com.company.changeassurance.domain.db.PackageImpactAssessment;
import com.company.changeassurance.domain.sql.PackageDeployDelta;

/**
 * Deterministic impact level and regression test scope from Oracle catalog evidence
 * plus optional deploy-script spec/body delta. AI must not override
 * {@link PackageImpactAssessment#overallImpact()}.
 */
public final class PackageImpactAssessmentCalculator {

    private final int largeDependentThreshold;

    public PackageImpactAssessmentCalculator(int largeDependentThreshold) {
        this.largeDependentThreshold = largeDependentThreshold <= 0 ? 4 : largeDependentThreshold;
    }

    public static PackageImpactAssessmentCalculator defaults() {
        return new PackageImpactAssessmentCalculator(4);
    }

    public PackageImpactAssessment assess(PackageDbImpact impact) {
        return assess(impact, PackageDeployDelta.none());
    }

    public PackageImpactAssessment assess(PackageDbImpact impact, PackageDeployDelta deployDelta) {
        if (impact == null || !impact.packageFound()) {
            return PackageImpactAssessment.unavailable(
                    "Package was not found in the catalog; impact cannot be fully assessed");
        }

        PackageDeployDelta delta = deployDelta == null ? PackageDeployDelta.none() : deployDelta;
        List<String> why = new ArrayList<>();
        int score = 0;

        if (delta.specChanged()) {
            why.add("Deploy script changes package specification (public API / contract risk)");
            score += 8;
        } else if (delta.bodyChanged()) {
            why.add("Deploy script changes package body only (implementation change)");
            score += 2;
        }
        if (delta.specChanged() && delta.bodyChanged()) {
            why.add("Deploy script also replaces package body");
            score += 1;
        }

        int direct = impact.dependents().size();
        if (direct > 0) {
            why.add(direct + " direct dependent object(s) identified");
            score += Math.min(direct, 8);
        }
        if (direct >= largeDependentThreshold) {
            why.add("Dependent blast radius meets/exceeds threshold (" + largeDependentThreshold + ")");
            score += 4;
        }

        int transitive = impact.transitiveDependents().size();
        if (transitive > 0) {
            why.add(transitive + " transitive dependent object(s) identified");
            score += Math.min(transitive, 10);
        }

        int jobs = impact.schedulerJobs().size();
        if (jobs > 0) {
            why.add(jobs + " scheduled job(s) reference the affected package");
            score += jobs * 3;
        }

        int invalid = impact.invalidRelatedObjects().size();
        if (invalid > 0) {
            why.add(invalid + " invalid/non-VALID related object(s) present");
            score += invalid * 3;
        }

        if (!"VALID".equalsIgnoreCase(impact.packageInfo().status())) {
            why.add("Target package status is " + impact.packageInfo().status());
            score += 5;
        }

        long tableDeps = impact.dependencyCountsByType().getOrDefault("TABLE", 0L);
        if (tableDeps > 0) {
            why.add(tableDeps + " table dependency(ies) used by the package");
            score += (int) Math.min(tableDeps, 4);
        }

        int sourceHits = impact.sourceReferences().size();
        if (sourceHits > 0) {
            why.add(sourceHits + " Oracle source reference hit(s) for the package name");
            score += Math.min(sourceHits / 3, 3);
        }

        if (why.isEmpty()) {
            why.add("Limited catalog edges discovered; residual uncertainty remains");
        }

        ImpactLevel level;
        if (score >= 12 || jobs >= 2 || invalid >= 2 || direct >= largeDependentThreshold + 2
                || (delta.specChanged() && direct >= 2)) {
            level = ImpactLevel.HIGH;
        } else if (score >= 5 || jobs >= 1 || invalid >= 1 || direct >= 2 || transitive >= 3
                || delta.specChanged()) {
            level = ImpactLevel.MEDIUM;
        } else {
            level = ImpactLevel.LOW;
        }

        List<String> tests = buildRecommendedTesting(impact, delta);
        String summary = "Overall impact " + level.name()
                + " for " + impact.packageInfo().qualifiedName()
                + " (score=" + score
                + (delta.hasPackageTouch() ? ", deploy=" + delta.changeKind() : "")
                + ")";
        return new PackageImpactAssessment(level, why, tests, summary);
    }

    private List<String> buildRecommendedTesting(PackageDbImpact impact, PackageDeployDelta delta) {
        List<String> tests = new ArrayList<>();
        tests.add("Compile package specification and body; confirm VALID status");
        if (delta.specChanged()) {
            tests.add("Regression-test all public package entry points and dependent callers "
                    + "(specification/API change)");
        } else if (delta.bodyChanged()) {
            tests.add("Regression-test changed package procedures/functions "
                    + "(body-only change; confirm signatures unchanged)");
        }
        if (!impact.dependents().isEmpty()) {
            tests.add("Regression-test direct dependents (" + impact.dependents().size()
                    + " object(s)): " + summarizeTypes(impact.dependentCountsByType()));
        }
        if (!impact.transitiveDependents().isEmpty()) {
            tests.add("Extend regression to transitive consumers ("
                    + impact.transitiveDependents().size() + " capped node(s))");
        }
        if (!impact.schedulerJobs().isEmpty()) {
            tests.add("Validate scheduler jobs that reference the package ("
                    + impact.schedulerJobs().size() + "); monitor first post-change runs");
        }
        long tables = impact.dependencyCountsByType().getOrDefault("TABLE", 0L);
        if (tables > 0) {
            tests.add("Integration-test DML paths against referenced table(s) (" + tables + ")");
        }
        if (!impact.invalidRelatedObjects().isEmpty()) {
            tests.add("Remediate/compile invalid related objects before release ("
                    + impact.invalidRelatedObjects().size() + ")");
        }
        if (impact.procedures().stream().anyMatch(p -> "FUNCTION".equalsIgnoreCase(p.procedureType()))) {
            tests.add("Unit-test package functions for expected return values and exception paths");
        }
        tests.add("Smoke-test primary calling paths identified in Oracle source references / wrappers");
        return List.copyOf(tests);
    }

    private static String summarizeTypes(java.util.Map<String, Long> counts) {
        if (counts == null || counts.isEmpty()) {
            return "mixed types";
        }
        StringBuilder sb = new StringBuilder();
        counts.forEach((type, count) -> {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(count).append(' ').append(type);
        });
        return sb.toString();
    }
}
