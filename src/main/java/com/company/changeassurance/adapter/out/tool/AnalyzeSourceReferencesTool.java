package com.company.changeassurance.adapter.out.tool;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.company.changeassurance.application.port.out.DatabaseMetadataPort;
import com.company.changeassurance.application.service.IdGenerator;
import com.company.changeassurance.configuration.ChangeAssuranceProperties;
import com.company.changeassurance.domain.db.DbSourceReference;
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
public class AnalyzeSourceReferencesTool implements AssuranceTool {

    private final IdGenerator ids;
    private final DatabaseMetadataPort databaseMetadataPort;
    private final int maxRows;

    public AnalyzeSourceReferencesTool(
            IdGenerator ids,
            DatabaseMetadataPort databaseMetadataPort,
            ChangeAssuranceProperties properties
    ) {
        this.ids = ids;
        this.databaseMetadataPort = databaseMetadataPort;
        this.maxRows = properties.investigation().sourceSearchMaxRows();
    }

    @Override
    public ToolType type() {
        return ToolType.ANALYZE_SOURCE_REFERENCES;
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
                    shell == null ? "Source reference analysis failed" : shell.summary());
        }

        try {
            String searchText = shell.packageInfo().packageName();
            List<DbSourceReference> refs = databaseMetadataPort.searchSourceReferences(
                    shell.packageInfo().owner(), searchText, maxRows);
            PackageDbImpact updated = shell.withSourceReferences(refs);
            review.setPackageDbImpact(updated);

            EvidenceId summary = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "source-references",
                    "Oracle source references",
                    "search=" + searchText + "; hits=" + refs.size() + "; maxRows=" + maxRows);

            if (refs.size() >= 10) {
                findings.add(FindingFactory.deterministic(
                        ids.nextFindingId(),
                        "DBI-008",
                        "Many source references to package",
                        searchText + " appears in " + refs.size() + " catalog source lines",
                        FindingSeverity.MEDIUM,
                        FindingCategory.DEPENDENCY_IMPACT,
                        List.of(summary),
                        "Review source hits for dynamic SQL and cross-schema callers not in ALL_DEPENDENCIES.",
                        type()
                ));
            }
            for (DbSourceReference ref : refs) {
                PackageImpactToolSupport.addEvidence(
                        evidence, ids,
                        "source:" + ref.qualifiedName() + ":" + ref.line(),
                        "Source reference (" + ref.objectType() + ")",
                        ref.qualifiedName() + " line " + ref.line() + ": " + ref.excerpt());
            }
            return ToolExecutionResult.success(findings, evidence,
                    "Source references=" + refs.size() + " via " + updated.catalogMode());
        } catch (RuntimeException ex) {
            EvidenceId eid = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "catalog-error", "Database metadata catalog error", ex.getMessage());
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(), "DBI-000", "Database catalog unavailable", ex.getMessage(),
                    FindingSeverity.CRITICAL, FindingCategory.DEPENDENCY_IMPACT, List.of(eid),
                    "Restore read-only catalog access or switch to fake metadata mode for local demos.",
                    type()));
            return ToolExecutionResult.success(findings, evidence, "Source references failed: catalog unavailable");
        }
    }
}
