package com.company.changeassurance.adapter.out.tool;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.company.changeassurance.application.port.out.DatabaseMetadataPort;
import com.company.changeassurance.application.service.IdGenerator;
import com.company.changeassurance.domain.db.DbPackageInfo;
import com.company.changeassurance.domain.db.DbPackageProcedure;
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
public class GetPackageObjectInfoTool implements AssuranceTool {

    private final IdGenerator ids;
    private final DatabaseMetadataPort databaseMetadataPort;

    public GetPackageObjectInfoTool(IdGenerator ids, DatabaseMetadataPort databaseMetadataPort) {
        this.ids = ids;
        this.databaseMetadataPort = databaseMetadataPort;
    }

    @Override
    public ToolType type() {
        return ToolType.GET_PACKAGE_OBJECT_INFO;
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
        if (shell == null) {
            return ToolExecutionResult.success(findings, evidence, "Package object info failed: catalog unavailable");
        }
        if (!shell.packageFound()) {
            return ToolExecutionResult.success(findings, evidence, shell.summary());
        }

        try {
            List<DbPackageProcedure> procedures = databaseMetadataPort.listPackageProcedures(
                    shell.packageInfo().owner(), shell.packageInfo().packageName());
            PackageDbImpact updated = shell.withProcedures(procedures);
            review.setPackageDbImpact(updated);
            PackageImpactToolSupport.addAffectedObjects(review, updated);

            DbPackageInfo info = updated.packageInfo();
            EvidenceId summary = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "package-object-info",
                    "Target package metadata",
                    info.qualifiedName() + " status=" + info.status()
                            + " spec=" + info.specPresent() + " body=" + info.bodyPresent()
                            + " procedures=" + procedures.size());

            if (!"VALID".equalsIgnoreCase(info.status())) {
                findings.add(FindingFactory.deterministic(
                        ids.nextFindingId(),
                        "DBI-002",
                        "Package status is not VALID",
                        info.qualifiedName() + " status=" + info.status(),
                        FindingSeverity.HIGH,
                        FindingCategory.DEPENDENCY_IMPACT,
                        List.of(summary),
                        "Resolve INVALID/compile errors before assessing change readiness.",
                        type()
                ));
            }
            if (!info.bodyPresent()) {
                findings.add(FindingFactory.deterministic(
                        ids.nextFindingId(),
                        "DBI-003",
                        "Package body missing",
                        info.qualifiedName() + " has specification but no body in catalog",
                        FindingSeverity.MEDIUM,
                        FindingCategory.DEPENDENCY_IMPACT,
                        List.of(summary),
                        "Confirm whether body deployment is pending or intentionally absent.",
                        type()
                ));
            }
            for (DbPackageProcedure proc : procedures) {
                PackageImpactToolSupport.addEvidence(evidence, ids, "procedure:" + proc.name(),
                        "Package " + proc.procedureType().toLowerCase(),
                        proc.name() + " (" + proc.procedureType() + ")");
            }
            return ToolExecutionResult.success(
                    findings, evidence,
                    "Package object info via " + updated.catalogMode() + ": " + info.qualifiedName()
                            + "; procedures=" + procedures.size());
        } catch (RuntimeException ex) {
            EvidenceId eid = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "catalog-error", "Database metadata catalog error", ex.getMessage());
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(), "DBI-000", "Database catalog unavailable", ex.getMessage(),
                    FindingSeverity.CRITICAL, FindingCategory.DEPENDENCY_IMPACT, List.of(eid),
                    "Restore read-only catalog access or switch to fake metadata mode for local demos.",
                    type()));
            return ToolExecutionResult.success(findings, evidence, "Package object info failed: catalog unavailable");
        }
    }
}
