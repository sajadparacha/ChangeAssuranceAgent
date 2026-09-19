package com.company.changeassurance.adapter.out.tool;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.company.changeassurance.application.port.out.DatabaseMetadataPort;
import com.company.changeassurance.application.service.IdGenerator;
import com.company.changeassurance.domain.db.DbObjectRef;
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

@Component
public class AnalyzePackageDependenciesTool implements AssuranceTool {

    private final IdGenerator ids;
    private final DatabaseMetadataPort databaseMetadataPort;

    public AnalyzePackageDependenciesTool(IdGenerator ids, DatabaseMetadataPort databaseMetadataPort) {
        this.ids = ids;
        this.databaseMetadataPort = databaseMetadataPort;
    }

    @Override
    public ToolType type() {
        return ToolType.ANALYZE_PACKAGE_DEPENDENCIES;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        ChangeReview review = request.context().review();
        if (PackageImpactToolSupport.requirePackageName(review) == null) {
            return PackageImpactToolSupport.missingPackageName();
        }

        List<Finding> findings = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        PackageDbImpact shell = PackageImpactToolSupport.ensureImpactShell(
                review, databaseMetadataPort, ids, findings, evidence, type());
        if (shell == null || !shell.packageFound()) {
            return ToolExecutionResult.success(findings, evidence,
                    shell == null ? "Package dependencies failed" : shell.summary());
        }

        try {
            List<DbObjectRef> dependencies = databaseMetadataPort.listDependencies(
                    shell.packageInfo().owner(), shell.packageInfo().packageName());
            PackageDbImpact updated = shell.withDependencies(dependencies);
            review.setPackageDbImpact(updated);
            PackageImpactToolSupport.addAffectedObjects(review, updated);

            EvidenceId summary = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "package-dependencies",
                    "Objects referenced by package",
                    updated.packageInfo().qualifiedName() + " dependencies=" + dependencies.size());

            long tableDeps = updated.dependencyCountsByType().getOrDefault("TABLE", 0L);
            if (tableDeps > 0) {
                findings.add(FindingFactory.deterministic(
                        ids.nextFindingId(),
                        "DBI-005",
                        "Package references table objects",
                        updated.packageInfo().qualifiedName() + " depends on " + tableDeps
                                + " table(s); data-volume and locking impact are unknown without live metrics",
                        FindingSeverity.MEDIUM,
                        FindingCategory.DEPENDENCY_IMPACT,
                        List.of(summary),
                        "Confirm DML scope, indexing, and offline/online window for referenced tables.",
                        type()
                ));
            }
            if (dependencies.isEmpty() && updated.dependents().isEmpty()) {
                findings.add(FindingFactory.deterministic(
                        ids.nextFindingId(),
                        "DBI-006",
                        "No dependency edges discovered",
                        "Catalog returned no dependencies for " + updated.packageInfo().qualifiedName(),
                        FindingSeverity.LOW,
                        FindingCategory.DEPENDENCY_IMPACT,
                        List.of(summary),
                        "Confirm dictionary privileges and that the package is not newly created empty.",
                        type()
                ));
            }
            for (DbObjectRef ref : dependencies) {
                PackageImpactToolSupport.addEvidence(evidence, ids, "dependency:" + ref.qualifiedName(),
                        "Referenced object (" + ref.objectType() + ")",
                        ref.qualifiedName() + " status=" + PackageImpactToolSupport.nullToUnknown(ref.status()));
            }
            return ToolExecutionResult.success(findings, evidence,
                    "Package dependencies=" + dependencies.size() + " via " + updated.catalogMode());
        } catch (RuntimeException ex) {
            EvidenceId eid = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "catalog-error", "Database metadata catalog error", ex.getMessage());
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(), "DBI-000", "Database catalog unavailable", ex.getMessage(),
                    FindingSeverity.CRITICAL, FindingCategory.DEPENDENCY_IMPACT, List.of(eid),
                    "Restore read-only catalog access or switch to fake metadata mode for local demos.",
                    type()));
            return ToolExecutionResult.success(findings, evidence, "Package dependencies failed: catalog unavailable");
        }
    }
}
