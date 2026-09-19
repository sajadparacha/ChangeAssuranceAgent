package com.company.changeassurance.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.company.changeassurance.domain.db.DbObjectRef;
import com.company.changeassurance.domain.db.DbPackageProcedure;
import com.company.changeassurance.domain.db.DbSchedulerJob;
import com.company.changeassurance.domain.db.DbSourceReference;
import com.company.changeassurance.domain.db.DbTransitiveNode;
import com.company.changeassurance.domain.db.PackageDbImpact;
import com.company.changeassurance.domain.db.PackageImpactAssessment;
import com.company.changeassurance.domain.model.AiAssessment;
import com.company.changeassurance.domain.model.AiContributionEntry;
import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.CriticResult;
import com.company.changeassurance.domain.model.Evidence;
import com.company.changeassurance.domain.model.EvidenceType;
import com.company.changeassurance.domain.model.Finding;
import com.company.changeassurance.domain.sql.PackageDeployDelta;

@Service
public class ReportAssembler {

    public Map<String, Object> assemble(ChangeReview review) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("identification", Map.of(
                "reviewId", review.getReviewId().value(),
                "application", review.getApplicationName(),
                "changeTitle", review.getChangeTitle(),
                "packageName", nullToEmpty(review.getPackageName()),
                "schemaOwner", nullToEmpty(review.getSchemaOwner()),
                "environment", nullToEmpty(review.getTargetEnvironment()),
                "createdAt", review.getCreatedAt().toString(),
                "completedAt", review.getCompletedAt() == null ? "" : review.getCompletedAt().toString()
        ));
        report.put("executiveSummary", review.getAiAssessment() == null
                ? defaultExecutiveSummary(review)
                : review.getAiAssessment().executiveSummary());
        report.put("agentUnderstanding", Map.of(
                "classification", review.getChangeClassification() == null
                        ? Map.of()
                        : review.getChangeClassification(),
                "affectedObjects", review.getAffectedObjects(),
                "aiAvailability", review.getAiAssessment() == null
                        ? "UNAVAILABLE"
                        : review.getAiAssessment().availabilityStatus()
        ));
        report.put("databaseImpact", assembleDatabaseImpact(review));
        report.put("reviewPlan", review.getReviewPlan());
        report.put("findingsBySeverity", groupFindings(review));
        report.put("informationGaps", review.getInformationGaps());
        report.put("riskScenarios", review.getRiskScenarios());
        report.put("riskAssessment", review.getRiskAssessment());
        report.put("recommendation", Map.of(
                "value", review.getReadinessRecommendation() == null
                        ? "INSUFFICIENT_INFORMATION"
                        : review.getReadinessRecommendation().name(),
                "deterministicReasonCodes", review.getDeterministicReasonCodes()
        ));
        report.put("requiredActions", review.getFindings().stream()
                .map(Finding::requiredAction)
                .filter(a -> a != null && !a.isBlank())
                .distinct()
                .toList());
        report.put("suggestedTests", mergeSuggestedTests(review));
        report.put("humanReviewQuestions", review.getAiAssessment() == null
                ? List.of()
                : review.getAiAssessment().humanReviewQuestions());
        report.put("criticReview", review.getCriticResult());
        report.put("aiContribution", assembleAiContribution(review));
        report.put("evidenceReferences", review.getEvidence());
        report.put("limitations", buildLimitations(review));
        report.put("humanApprovalRequired", true);
        report.put("status", review.getReviewStatus());
        report.put("currentStage", review.getCurrentStage());
        return report;
    }

    private Map<String, Object> assembleDatabaseImpact(ChangeReview review) {
        PackageDbImpact impact = review.getPackageDbImpact();
        if (impact == null) {
            if (!review.isPackageImpactMode()) {
                return Map.of("available", false);
            }
            return Map.of(
                    "available", false,
                    "requestedPackage", nullToEmpty(review.getPackageName()),
                    "schemaOwner", nullToEmpty(review.getSchemaOwner()),
                    "message", "Database impact analysis did not produce a catalog result"
            );
        }
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("available", true);
        section.put("packageFound", impact.packageFound());
        section.put("catalogMode", impact.catalogMode());
        section.put("summary", impact.summary());
        section.put("package", Map.of(
                "owner", nullToEmpty(impact.packageInfo().owner()),
                "name", impact.packageInfo().packageName(),
                "qualifiedName", impact.packageInfo().qualifiedName(),
                "specPresent", impact.packageInfo().specPresent(),
                "bodyPresent", impact.packageInfo().bodyPresent(),
                "status", impact.packageInfo().status()
        ));
        section.put("procedures", impact.procedures().stream().map(this::procedureMap).toList());
        section.put("dependencies", impact.dependencies().stream().map(this::objectMap).toList());
        section.put("dependents", impact.dependents().stream().map(this::objectMap).toList());
        section.put("transitiveDependents", impact.transitiveDependents().stream()
                .map(this::transitiveMap).toList());
        section.put("sourceReferences", impact.sourceReferences().stream()
                .map(this::sourceMap).toList());
        section.put("schedulerJobs", impact.schedulerJobs().stream().map(this::jobMap).toList());
        section.put("invalidRelatedObjects", impact.invalidRelatedObjects().stream()
                .map(this::objectMap).toList());
        section.put("dependencyCountsByType", impact.dependencyCountsByType());
        section.put("dependentCountsByType", impact.dependentCountsByType());
        section.put("blastRadius", Map.of(
                "dependentCount", impact.dependents().size(),
                "dependencyCount", impact.dependencies().size(),
                "procedureCount", impact.procedures().size(),
                "transitiveCount", impact.transitiveDependents().size(),
                "schedulerJobCount", impact.schedulerJobs().size(),
                "invalidRelatedCount", impact.invalidRelatedObjects().size(),
                "sourceReferenceCount", impact.sourceReferences().size()
        ));
        PackageImpactAssessment assessment = impact.impactAssessment();
        if (assessment != null) {
            section.put("overallImpact", assessment.overallImpact().name());
            section.put("why", assessment.why());
            section.put("recommendedTesting", assessment.recommendedTesting());
            section.put("impactSummary", assessment.summary());
        } else {
            section.put("overallImpact", "");
            section.put("why", List.of());
            section.put("recommendedTesting", List.of());
            section.put("impactSummary", "");
        }
        PackageDeployDelta delta = review.getPackageDeployDelta();
        section.put("deployChange", Map.of(
                "specChanged", delta.specChanged(),
                "bodyChanged", delta.bodyChanged(),
                "changeKind", delta.changeKind(),
                "inferredFromScript", delta.inferredFromScript(),
                "packagesInScript", delta.packageNames()
        ));
        return section;
    }

    private List<String> mergeSuggestedTests(ChangeReview review) {
        List<String> tests = new java.util.ArrayList<>();
        if (review.getPackageDbImpact() != null
                && review.getPackageDbImpact().impactAssessment() != null) {
            tests.addAll(review.getPackageDbImpact().impactAssessment().recommendedTesting());
        }
        if (review.getAiAssessment() != null && review.getAiAssessment().suggestedTests() != null) {
            for (String t : review.getAiAssessment().suggestedTests()) {
                if (t != null && !t.isBlank() && !tests.contains(t)) {
                    tests.add(t);
                }
            }
        }
        return List.copyOf(tests);
    }

    private Map<String, Object> procedureMap(DbPackageProcedure procedure) {
        return Map.of(
                "name", procedure.name(),
                "procedureType", procedure.procedureType()
        );
    }

    private Map<String, Object> objectMap(DbObjectRef ref) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("owner", nullToEmpty(ref.owner()));
        map.put("objectName", ref.objectName());
        map.put("objectType", ref.objectType());
        map.put("status", nullToEmpty(ref.status()));
        map.put("qualifiedName", ref.qualifiedName());
        return map;
    }

    private Map<String, Object> transitiveMap(DbTransitiveNode node) {
        Map<String, Object> map = objectMap(node.object());
        map.put("depth", node.depth());
        return map;
    }

    private Map<String, Object> sourceMap(DbSourceReference ref) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("owner", nullToEmpty(ref.owner()));
        map.put("objectName", ref.objectName());
        map.put("objectType", ref.objectType());
        map.put("line", ref.line());
        map.put("excerpt", ref.excerpt());
        map.put("qualifiedName", ref.qualifiedName());
        return map;
    }

    private Map<String, Object> jobMap(DbSchedulerJob job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("owner", nullToEmpty(job.owner()));
        map.put("jobName", job.jobName());
        map.put("jobType", job.jobType());
        map.put("enabled", job.enabled());
        map.put("state", job.state());
        map.put("actionExcerpt", job.actionExcerpt());
        map.put("qualifiedName", job.qualifiedName());
        return map;
    }

    private Map<String, Object> assembleAiContribution(ChangeReview review) {
        AiAssessment assessment = review.getAiAssessment();
        CriticResult critic = review.getCriticResult();
        boolean gatewayUsed = assessment != null
                && !AiAssessment.UNAVAILABLE.equals(assessment.availabilityStatus());

        List<Map<String, Object>> tasks = review.getAiContributions().stream()
                .map(this::aiTaskMap)
                .toList();
        if (tasks.isEmpty()) {
            tasks = List.of(Map.of(
                    "taskName", "No AI contribution recorded",
                    "purpose", "This review completed before AI contribution tracking was available",
                    "howExecuted", AiContributionEntry.SKIPPED,
                    "outcome", "Re-run the review to populate this section"
            ));
        }

        long liveCount = review.getAiContributions().stream()
                .filter(e -> AiContributionEntry.LIVE_MODEL.equals(e.howExecuted()))
                .count();

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("summary", liveCount > 0
                ? "A live model participated in at least one reasoning step; deterministic policy still owns findings, risk, impact, and recommendation."
                : (gatewayUsed
                ? "AI gateway was available; narrative/critic steps used controlled templates or rules. Deterministic tools produced findings and the recommendation."
                : "No live model was required for the decisive outcomes; deterministic analysis produced findings, risk, and recommendation."));
        section.put("selectedProvider", nullToEmpty(review.getPreferredAiProvider()));
        section.put("selectedModel", assessment != null && assessment.modelIdentifier() != null
                ? assessment.modelIdentifier()
                : nullToEmpty(review.getPreferredAiModel()));
        section.put("promptVersion", assessment == null ? "" : nullToEmpty(assessment.promptVersion()));
        section.put("draftReportStatus", assessment == null
                ? AiAssessment.UNAVAILABLE
                : assessment.availabilityStatus());
        section.put("criticStatus", critic == null
                ? CriticResult.UNAVAILABLE
                : critic.availabilityStatus());
        section.put("liveModelTasks", liveCount);
        section.put("cannotOverride", List.of(
                "SQL safety findings",
                "Deterministic risk score",
                "Readiness recommendation (GO / CONDITIONAL_GO / NO_GO)",
                "Package overall impact level (when catalog impact runs)",
                "Human approval requirement"
        ));
        section.put("tasks", tasks);
        return section;
    }

    private Map<String, Object> aiTaskMap(AiContributionEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskName", entry.taskName());
        map.put("purpose", entry.purpose());
        map.put("howExecuted", entry.howExecuted());
        map.put("outcome", entry.outcome());
        return map;
    }

    private List<String> buildLimitations(ChangeReview review) {
        List<String> limitations = new java.util.ArrayList<>();
        limitations.add("No change SQL was executed");
        PackageDbImpact impact = review.getPackageDbImpact();
        if (impact == null) {
            limitations.add("No database catalog metadata was queried");
            limitations.add("No production system was accessed");
        } else if ("fake".equalsIgnoreCase(impact.catalogMode())) {
            limitations.add("Database impact used the fake in-memory catalog (demo mode)");
            limitations.add("No live Oracle dictionary views were queried");
        } else {
            limitations.add("Oracle dictionary views were queried read-only for package impact");
            limitations.add("No change SQL / DML / DDL was executed against the database");
        }
        limitations.add("Application source code was not scanned");
        limitations.add("Conclusions depend on catalog privileges and submitted evidence");
        limitations.add("Deterministic overall impact level cannot be overridden by AI");
        limitations.add("Human approval remains mandatory");
        return List.copyOf(limitations);
    }

    private String defaultExecutiveSummary(ChangeReview review) {
        if (review.getPackageDbImpact() != null) {
            PackageImpactAssessment assessment = review.getPackageDbImpact().impactAssessment();
            if (assessment != null) {
                return "Deterministic package database-impact review complete. "
                        + assessment.summary()
                        + " Human approval is mandatory.";
            }
            return "Deterministic package database-impact review complete. "
                    + review.getPackageDbImpact().summary()
                    + " Human approval is mandatory.";
        }
        return "Deterministic first-pass review complete. Human approval is mandatory.";
    }

    private Map<String, List<Map<String, Object>>> groupFindings(ChangeReview review) {
        Map<String, Evidence> evidenceById = new LinkedHashMap<>();
        for (Evidence evidence : review.getEvidence()) {
            evidenceById.put(evidence.evidenceId().value(), evidence);
        }

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Finding finding : review.getFindings()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("findingId", finding.findingId());
            row.put("ruleCode", finding.ruleCode());
            row.put("title", finding.title());
            row.put("description", finding.description());
            row.put("severity", finding.severity());
            row.put("category", finding.category());
            row.put("evidenceIds", finding.evidenceIds());
            row.put("requiredAction", finding.requiredAction());
            row.put("deterministic", finding.deterministic());
            row.put("status", finding.status());
            row.put("sourceTool", finding.sourceTool());

            Evidence sqlEvidence = resolveSqlEvidence(finding, evidenceById);
            if (sqlEvidence != null) {
                row.put("sqlLine", sqlEvidence.lineNumber());
                row.put("sqlSnippet", sqlEvidence.extractedContent());
                row.put("sqlSourceReference", sqlEvidence.sourceReference());
            }

            grouped.computeIfAbsent(finding.severity().name(), ignored -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private static Evidence resolveSqlEvidence(Finding finding, Map<String, Evidence> evidenceById) {
        Evidence fallback = null;
        for (var evidenceId : finding.evidenceIds()) {
            Evidence evidence = evidenceById.get(evidenceId.value());
            if (evidence == null) {
                continue;
            }
            boolean hasSqlText = evidence.extractedContent() != null && !evidence.extractedContent().isBlank();
            if (!hasSqlText) {
                continue;
            }
            if (evidence.evidenceType() == EvidenceType.SQL_STATEMENT) {
                return evidence;
            }
            if (fallback == null && evidence.lineNumber() != null) {
                fallback = evidence;
            }
        }
        return fallback;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
