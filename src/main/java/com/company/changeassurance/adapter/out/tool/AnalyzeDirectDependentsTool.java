package com.company.changeassurance.adapter.out.tool;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.company.changeassurance.application.port.out.DatabaseMetadataPort;
import com.company.changeassurance.application.service.IdGenerator;
import com.company.changeassurance.configuration.ChangeAssuranceProperties;
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
public class AnalyzeDirectDependentsTool implements AssuranceTool {

    private final IdGenerator ids;
    private final DatabaseMetadataPort databaseMetadataPort;
    private final int largeDependentThreshold;

    public AnalyzeDirectDependentsTool(
            IdGenerator ids,
            DatabaseMetadataPort databaseMetadataPort,
            ChangeAssuranceProperties properties
    ) {
        this.ids = ids;
        this.databaseMetadataPort = databaseMetadataPort;
        this.largeDependentThreshold = properties.investigation().largeDependentThreshold();
    }

    @Override
    public ToolType type() {
        return ToolType.ANALYZE_DIRECT_DEPENDENTS;
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
                    shell == null ? "Direct dependents failed" : shell.summary());
        }

        try {
            List<DbObjectRef> dependents = databaseMetadataPort.listDependents(
                    shell.packageInfo().owner(), shell.packageInfo().packageName());
            PackageDbImpact updated = shell.withDependents(dependents);
            review.setPackageDbImpact(updated);
            PackageImpactToolSupport.addAffectedObjects(review, updated);

            EvidenceId summary = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "direct-dependents",
                    "Direct dependents (blast radius)",
                    updated.packageInfo().qualifiedName() + " dependents=" + dependents.size());

            if (dependents.size() >= largeDependentThreshold) {
                findings.add(FindingFactory.deterministic(
                        ids.nextFindingId(),
                        "DBI-004",
                        "Large dependent blast radius",
                        updated.packageInfo().qualifiedName() + " has " + dependents.size()
                                + " dependent objects",
                        FindingSeverity.HIGH,
                        FindingCategory.DEPENDENCY_IMPACT,
                        List.of(summary),
                        "Review each dependent object for regression and coordinated deployment.",
                        type()
                ));
            }
            for (DbObjectRef ref : dependents) {
                PackageImpactToolSupport.addEvidence(evidence, ids, "dependent:" + ref.qualifiedName(),
                        "Dependent object (" + ref.objectType() + ")",
                        ref.qualifiedName() + " status=" + PackageImpactToolSupport.nullToUnknown(ref.status()));
            }
            return ToolExecutionResult.success(findings, evidence,
                    "Direct dependents=" + dependents.size() + " via " + updated.catalogMode());
        } catch (RuntimeException ex) {
            EvidenceId eid = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "catalog-error", "Database metadata catalog error", ex.getMessage());
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(), "DBI-000", "Database catalog unavailable", ex.getMessage(),
                    FindingSeverity.CRITICAL, FindingCategory.DEPENDENCY_IMPACT, List.of(eid),
                    "Restore read-only catalog access or switch to fake metadata mode for local demos.",
                    type()));
            return ToolExecutionResult.success(findings, evidence, "Direct dependents failed: catalog unavailable");
        }
    }
}
