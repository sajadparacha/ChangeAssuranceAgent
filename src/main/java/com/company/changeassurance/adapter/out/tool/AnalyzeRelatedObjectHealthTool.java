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
public class AnalyzeRelatedObjectHealthTool implements AssuranceTool {

    private final IdGenerator ids;
    private final DatabaseMetadataPort databaseMetadataPort;

    public AnalyzeRelatedObjectHealthTool(IdGenerator ids, DatabaseMetadataPort databaseMetadataPort) {
        this.ids = ids;
        this.databaseMetadataPort = databaseMetadataPort;
    }

    @Override
    public ToolType type() {
        return ToolType.ANALYZE_RELATED_OBJECT_HEALTH;
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
                    shell == null ? "Object health analysis failed" : shell.summary());
        }

        try {
            List<DbObjectRef> invalid = databaseMetadataPort.listInvalidRelatedObjects(
                    shell.packageInfo().owner(), shell.packageInfo().packageName());
            PackageDbImpact updated = shell.withInvalidRelatedObjects(invalid);
            review.setPackageDbImpact(updated);

            EvidenceId summary = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "object-health",
                    "Related object health",
                    updated.packageInfo().qualifiedName() + " invalidRelated=" + invalid.size());

            if (!invalid.isEmpty()) {
                findings.add(FindingFactory.deterministic(
                        ids.nextFindingId(),
                        "DBI-010",
                        "Invalid related objects detected",
                        updated.packageInfo().qualifiedName() + " has " + invalid.size()
                                + " non-VALID related object(s)",
                        FindingSeverity.HIGH,
                        FindingCategory.DEPENDENCY_IMPACT,
                        List.of(summary),
                        "Compile or remediate invalid related objects before production change.",
                        type()
                ));
            }
            for (DbObjectRef ref : invalid) {
                PackageImpactToolSupport.addEvidence(
                        evidence, ids, "invalid:" + ref.qualifiedName(),
                        "Invalid related object (" + ref.objectType() + ")",
                        ref.qualifiedName() + " status=" + PackageImpactToolSupport.nullToUnknown(ref.status()));
            }
            return ToolExecutionResult.success(findings, evidence,
                    "Invalid related objects=" + invalid.size() + " via " + updated.catalogMode());
        } catch (RuntimeException ex) {
            EvidenceId eid = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "catalog-error", "Database metadata catalog error", ex.getMessage());
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(), "DBI-000", "Database catalog unavailable", ex.getMessage(),
                    FindingSeverity.CRITICAL, FindingCategory.DEPENDENCY_IMPACT, List.of(eid),
                    "Restore read-only catalog access or switch to fake metadata mode for local demos.",
                    type()));
            return ToolExecutionResult.success(findings, evidence, "Object health failed: catalog unavailable");
        }
    }
}
