package com.company.changeassurance.adapter.out.tool;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.company.changeassurance.application.port.out.DatabaseMetadataPort;
import com.company.changeassurance.application.service.IdGenerator;
import com.company.changeassurance.configuration.ChangeAssuranceProperties;
import com.company.changeassurance.domain.db.DbObjectRef;
import com.company.changeassurance.domain.db.DbPackageProcedure;
import com.company.changeassurance.domain.db.DbSchedulerJob;
import com.company.changeassurance.domain.db.DbSourceReference;
import com.company.changeassurance.domain.db.DbTransitiveNode;
import com.company.changeassurance.domain.db.PackageDbImpact;
import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.Evidence;
import com.company.changeassurance.domain.model.EvidenceId;
import com.company.changeassurance.domain.model.Finding;
import com.company.changeassurance.domain.model.FindingCategory;
import com.company.changeassurance.domain.model.FindingSeverity;
import com.company.changeassurance.domain.model.ToolType;
import com.company.changeassurance.domain.rule.AssuranceTool;
import com.company.changeassurance.domain.rule.ToolExecutionRequest;
import com.company.changeassurance.domain.rule.ToolExecutionResult;
import com.company.changeassurance.domain.service.FindingFactory;

/**
 * Compatibility orchestrator that runs a full catalog impact snapshot.
 * Prefer discrete package-impact tools for new plans.
 */
@Component
public class PackageDbImpactTool implements AssuranceTool {

    private final IdGenerator ids;
    private final DatabaseMetadataPort databaseMetadataPort;
    private final ChangeAssuranceProperties properties;

    public PackageDbImpactTool(
            IdGenerator ids,
            DatabaseMetadataPort databaseMetadataPort,
            ChangeAssuranceProperties properties
    ) {
        this.ids = ids;
        this.databaseMetadataPort = databaseMetadataPort;
        this.properties = properties;
    }

    @Override
    public ToolType type() {
        return ToolType.ANALYZE_PACKAGE_DB_IMPACT;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        ChangeReview review = request.context().review();
        if (PackageImpactToolSupport.requirePackageName(review) == null) {
            return PackageImpactToolSupport.missingPackageName();
        }

        List<Finding> findings = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        var inv = properties.investigation();

        PackageDbImpact impact;
        try {
            impact = databaseMetadataPort.analyzeImpact(
                    review.getSchemaOwner(),
                    review.getPackageName(),
                    inv.transitiveMaxDepth(),
                    inv.transitiveMaxNodes(),
                    inv.sourceSearchMaxRows());
        } catch (RuntimeException ex) {
            EvidenceId eid = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "catalog-error", "Database metadata catalog error", ex.getMessage());
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(),
                    "DBI-000",
                    "Database catalog unavailable",
                    ex.getMessage(),
                    FindingSeverity.CRITICAL,
                    FindingCategory.DEPENDENCY_IMPACT,
                    List.of(eid),
                    "Restore read-only catalog access or switch to fake metadata mode for local demos.",
                    type()
            ));
            return ToolExecutionResult.success(findings, evidence, "Package DB impact failed: catalog unavailable");
        }

        review.setPackageDbImpact(impact);
        PackageImpactToolSupport.addAffectedObjects(review, impact);

        EvidenceId summaryEvidence = PackageImpactToolSupport.addEvidence(
                evidence, ids, "package-impact-summary",
                "Package database impact summary", impact.summary());

        if (!impact.packageFound()) {
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(),
                    "DBI-001",
                    "Package not found in catalog",
                    impact.summary(),
                    FindingSeverity.CRITICAL,
                    FindingCategory.DEPENDENCY_IMPACT,
                    List.of(summaryEvidence),
                    "Verify package name and schema owner, then resubmit.",
                    type()
            ));
            return ToolExecutionResult.success(findings, evidence, impact.summary());
        }

        if (!"VALID".equalsIgnoreCase(impact.packageInfo().status())) {
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(),
                    "DBI-002",
                    "Package status is not VALID",
                    impact.packageInfo().qualifiedName() + " status=" + impact.packageInfo().status(),
                    FindingSeverity.HIGH,
                    FindingCategory.DEPENDENCY_IMPACT,
                    List.of(summaryEvidence),
                    "Resolve INVALID/compile errors before assessing change readiness.",
                    type()
            ));
        }

        if (!impact.packageInfo().bodyPresent()) {
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(),
                    "DBI-003",
                    "Package body missing",
                    impact.packageInfo().qualifiedName() + " has specification but no body in catalog",
                    FindingSeverity.MEDIUM,
                    FindingCategory.DEPENDENCY_IMPACT,
                    List.of(summaryEvidence),
                    "Confirm whether body deployment is pending or intentionally absent.",
                    type()
            ));
        }

        if (impact.dependents().size() >= inv.largeDependentThreshold()) {
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(),
                    "DBI-004",
                    "Large dependent blast radius",
                    impact.packageInfo().qualifiedName() + " has " + impact.dependents().size()
                            + " dependent objects",
                    FindingSeverity.HIGH,
                    FindingCategory.DEPENDENCY_IMPACT,
                    List.of(summaryEvidence),
                    "Review each dependent object for regression and coordinated deployment.",
                    type()
            ));
        }

        long tableDeps = impact.dependencyCountsByType().getOrDefault("TABLE", 0L);
        if (tableDeps > 0) {
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(),
                    "DBI-005",
                    "Package references table objects",
                    impact.packageInfo().qualifiedName() + " depends on " + tableDeps
                            + " table(s); data-volume and locking impact are unknown without live metrics",
                    FindingSeverity.MEDIUM,
                    FindingCategory.DEPENDENCY_IMPACT,
                    List.of(summaryEvidence),
                    "Confirm DML scope, indexing, and offline/online window for referenced tables.",
                    type()
            ));
        }

        if (impact.dependents().isEmpty() && impact.dependencies().isEmpty()) {
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(),
                    "DBI-006",
                    "No dependency edges discovered",
                    "Catalog returned no dependencies or dependents for "
                            + impact.packageInfo().qualifiedName(),
                    FindingSeverity.LOW,
                    FindingCategory.DEPENDENCY_IMPACT,
                    List.of(summaryEvidence),
                    "Confirm dictionary privileges and that the package is not newly created empty.",
                    type()
            ));
        }

        if (!impact.schedulerJobs().isEmpty()) {
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(),
                    "DBI-009",
                    "Scheduled jobs reference package",
                    impact.packageInfo().qualifiedName() + " is referenced by "
                            + impact.schedulerJobs().size() + " scheduler job(s)",
                    FindingSeverity.HIGH,
                    FindingCategory.DEPENDENCY_IMPACT,
                    List.of(summaryEvidence),
                    "Coordinate job disable/enable windows and validate post-change job runs.",
                    type()
            ));
        }

        if (!impact.invalidRelatedObjects().isEmpty()) {
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(),
                    "DBI-010",
                    "Invalid related objects detected",
                    impact.packageInfo().qualifiedName() + " has "
                            + impact.invalidRelatedObjects().size() + " non-VALID related object(s)",
                    FindingSeverity.HIGH,
                    FindingCategory.DEPENDENCY_IMPACT,
                    List.of(summaryEvidence),
                    "Compile or remediate invalid related objects before production change.",
                    type()
            ));
        }

        for (DbPackageProcedure proc : impact.procedures()) {
            PackageImpactToolSupport.addEvidence(evidence, ids, "procedure:" + proc.name(),
                    "Package " + proc.procedureType().toLowerCase(),
                    proc.name() + " (" + proc.procedureType() + ")");
        }
        for (DbObjectRef ref : impact.dependencies()) {
            PackageImpactToolSupport.addEvidence(evidence, ids, "dependency:" + ref.qualifiedName(),
                    "Referenced object (" + ref.objectType() + ")",
                    ref.qualifiedName() + " status=" + PackageImpactToolSupport.nullToUnknown(ref.status()));
        }
        for (DbObjectRef ref : impact.dependents()) {
            PackageImpactToolSupport.addEvidence(evidence, ids, "dependent:" + ref.qualifiedName(),
                    "Dependent object (" + ref.objectType() + ")",
                    ref.qualifiedName() + " status=" + PackageImpactToolSupport.nullToUnknown(ref.status()));
        }
        for (DbTransitiveNode node : impact.transitiveDependents()) {
            PackageImpactToolSupport.addEvidence(evidence, ids,
                    "transitive:" + node.object().qualifiedName(),
                    "Transitive dependent depth=" + node.depth(),
                    node.object().qualifiedName());
        }
        for (DbSourceReference ref : impact.sourceReferences()) {
            PackageImpactToolSupport.addEvidence(evidence, ids,
                    "source:" + ref.qualifiedName() + ":" + ref.line(),
                    "Source reference",
                    ref.excerpt());
        }
        for (DbSchedulerJob job : impact.schedulerJobs()) {
            PackageImpactToolSupport.addEvidence(evidence, ids, "job:" + job.qualifiedName(),
                    "Scheduler job",
                    job.qualifiedName() + " " + job.actionExcerpt());
        }
        for (DbObjectRef ref : impact.invalidRelatedObjects()) {
            PackageImpactToolSupport.addEvidence(evidence, ids, "invalid:" + ref.qualifiedName(),
                    "Invalid related object",
                    ref.qualifiedName() + " status=" + PackageImpactToolSupport.nullToUnknown(ref.status()));
        }

        return ToolExecutionResult.success(
                findings,
                evidence,
                "Package DB impact via " + impact.catalogMode() + " catalog: " + impact.summary()
                        + "; findings=" + findings.size()
        );
    }
}
