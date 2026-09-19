package com.company.changeassurance.adapter.out.tool;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.company.changeassurance.application.port.out.DatabaseMetadataPort;
import com.company.changeassurance.application.service.IdGenerator;
import com.company.changeassurance.configuration.ChangeAssuranceProperties;
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

@Component
public class AnalyzeTransitiveDependentsTool implements AssuranceTool {

    private final IdGenerator ids;
    private final DatabaseMetadataPort databaseMetadataPort;
    private final int maxDepth;
    private final int maxNodes;

    public AnalyzeTransitiveDependentsTool(
            IdGenerator ids,
            DatabaseMetadataPort databaseMetadataPort,
            ChangeAssuranceProperties properties
    ) {
        this.ids = ids;
        this.databaseMetadataPort = databaseMetadataPort;
        this.maxDepth = properties.investigation().transitiveMaxDepth();
        this.maxNodes = properties.investigation().transitiveMaxNodes();
    }

    @Override
    public ToolType type() {
        return ToolType.ANALYZE_TRANSITIVE_DEPENDENTS;
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
                    shell == null ? "Transitive dependents failed" : shell.summary());
        }

        try {
            List<DbTransitiveNode> transitive = databaseMetadataPort.listTransitiveDependents(
                    shell.packageInfo().owner(),
                    shell.packageInfo().packageName(),
                    maxDepth,
                    maxNodes);
            PackageDbImpact updated = shell.withTransitiveDependents(transitive);
            review.setPackageDbImpact(updated);
            PackageImpactToolSupport.addAffectedObjects(review, updated);

            EvidenceId summary = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "transitive-dependents",
                    "Transitive dependents (capped)",
                    updated.packageInfo().qualifiedName()
                            + " transitive=" + transitive.size()
                            + " maxDepth=" + maxDepth
                            + " maxNodes=" + maxNodes);

            if (transitive.size() >= 5) {
                findings.add(FindingFactory.deterministic(
                        ids.nextFindingId(),
                        "DBI-007",
                        "Broad transitive dependency chain",
                        updated.packageInfo().qualifiedName() + " has " + transitive.size()
                                + " transitive dependents within depth " + maxDepth,
                        FindingSeverity.HIGH,
                        FindingCategory.DEPENDENCY_IMPACT,
                        List.of(summary),
                        "Expand regression scope to second-order consumers and coordinated release partners.",
                        type()
                ));
            }
            for (DbTransitiveNode node : transitive) {
                PackageImpactToolSupport.addEvidence(
                        evidence, ids,
                        "transitive:" + node.object().qualifiedName(),
                        "Transitive dependent depth=" + node.depth(),
                        node.object().qualifiedName() + " (" + node.object().objectType() + ")"
                                + " status=" + PackageImpactToolSupport.nullToUnknown(node.object().status()));
            }
            return ToolExecutionResult.success(findings, evidence,
                    "Transitive dependents=" + transitive.size() + " via " + updated.catalogMode());
        } catch (RuntimeException ex) {
            EvidenceId eid = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "catalog-error", "Database metadata catalog error", ex.getMessage());
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(), "DBI-000", "Database catalog unavailable", ex.getMessage(),
                    FindingSeverity.CRITICAL, FindingCategory.DEPENDENCY_IMPACT, List.of(eid),
                    "Restore read-only catalog access or switch to fake metadata mode for local demos.",
                    type()));
            return ToolExecutionResult.success(findings, evidence, "Transitive dependents failed: catalog unavailable");
        }
    }
}
