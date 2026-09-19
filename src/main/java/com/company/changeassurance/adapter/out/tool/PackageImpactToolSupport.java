package com.company.changeassurance.adapter.out.tool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.company.changeassurance.application.port.out.DatabaseMetadataPort;
import com.company.changeassurance.domain.db.DbObjectRef;
import com.company.changeassurance.domain.db.DbPackageInfo;
import com.company.changeassurance.domain.db.PackageDbImpact;
import com.company.changeassurance.domain.model.AffectedObject;
import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.Evidence;
import com.company.changeassurance.domain.model.EvidenceId;
import com.company.changeassurance.domain.model.EvidenceSource;
import com.company.changeassurance.domain.model.EvidenceType;
import com.company.changeassurance.domain.model.Finding;
import com.company.changeassurance.domain.model.FindingCategory;
import com.company.changeassurance.domain.model.FindingSeverity;
import com.company.changeassurance.domain.model.ToolType;
import com.company.changeassurance.domain.rule.ToolExecutionResult;
import com.company.changeassurance.domain.service.FindingFactory;
import com.company.changeassurance.application.service.IdGenerator;

/**
 * Shared helpers for discrete package DB impact tools.
 */
final class PackageImpactToolSupport {

    private PackageImpactToolSupport() {
    }

    static String requirePackageName(ChangeReview review) {
        String packageName = review.getPackageName();
        if (packageName == null || packageName.isBlank()) {
            return null;
        }
        return packageName;
    }

    static ToolExecutionResult missingPackageName() {
        return ToolExecutionResult.failure(
                "PACKAGE_NAME_MISSING",
                "packageName is required for database impact analysis");
    }

    static PackageDbImpact ensureImpactShell(
            ChangeReview review,
            DatabaseMetadataPort port,
            IdGenerator ids,
            List<Finding> findings,
            List<Evidence> evidence,
            ToolType toolType
    ) {
        PackageDbImpact existing = review.getPackageDbImpact();
        if (existing != null && existing.packageFound()) {
            return existing;
        }
        if (existing != null && !existing.packageFound()) {
            return existing;
        }
        try {
            var found = port.findPackage(review.getSchemaOwner(), review.getPackageName());
            if (found.isEmpty()) {
                PackageDbImpact notFound = PackageDbImpact.notFound(
                        port.catalogMode(), review.getSchemaOwner(), review.getPackageName());
                review.setPackageDbImpact(notFound);
                EvidenceId eid = addEvidence(evidence, ids, "package-not-found",
                        "Package not found in catalog", notFound.summary());
                findings.add(FindingFactory.deterministic(
                        ids.nextFindingId(),
                        "DBI-001",
                        "Package not found in catalog",
                        notFound.summary(),
                        FindingSeverity.CRITICAL,
                        FindingCategory.DEPENDENCY_IMPACT,
                        List.of(eid),
                        "Verify package name and schema owner, then resubmit.",
                        toolType
                ));
                return notFound;
            }
            DbPackageInfo info = found.get();
            PackageDbImpact shell = PackageDbImpact.of(
                    port.catalogMode(),
                    info,
                    List.of(),
                    List.of(),
                    List.of()
            );
            review.setPackageDbImpact(shell);
            return shell;
        } catch (RuntimeException ex) {
            EvidenceId eid = addEvidence(evidence, ids, "catalog-error",
                    "Database metadata catalog error", ex.getMessage());
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(),
                    "DBI-000",
                    "Database catalog unavailable",
                    ex.getMessage(),
                    FindingSeverity.CRITICAL,
                    FindingCategory.DEPENDENCY_IMPACT,
                    List.of(eid),
                    "Restore read-only catalog access or switch to fake metadata mode for local demos.",
                    toolType
            ));
            return null;
        }
    }

    static EvidenceId addEvidence(
            List<Evidence> evidence,
            IdGenerator ids,
            String ref,
            String description,
            String excerpt
    ) {
        EvidenceId eid = new EvidenceId(ids.nextEvidenceId());
        String safe = excerpt == null ? "" : excerpt;
        if (safe.length() > 500) {
            safe = safe.substring(0, 500) + "...";
        }
        evidence.add(new Evidence(
                eid,
                EvidenceType.TOOL_RESULT,
                EvidenceSource.DETERMINISTIC_TOOL,
                ref,
                description,
                safe,
                null,
                null,
                Instant.now()
        ));
        return eid;
    }

    static void addAffectedObjects(ChangeReview review, PackageDbImpact impact) {
        if (impact == null || !impact.packageFound()) {
            return;
        }
        Set<String> existing = new HashSet<>();
        for (AffectedObject existingObject : review.getAffectedObjects()) {
            existing.add(key(existingObject.objectName(), existingObject.changeKind()));
        }
        addAffected(review, existing, impact.packageInfo().packageName(), "PACKAGE",
                impact.packageInfo().owner(), "CATALOG_PACKAGE");
        for (DbObjectRef ref : impact.dependencies()) {
            addAffected(review, existing, ref.objectName(), ref.objectType(), ref.owner(), "DEPENDENCY");
        }
        for (DbObjectRef ref : impact.dependents()) {
            addAffected(review, existing, ref.objectName(), ref.objectType(), ref.owner(), "DEPENDENT");
        }
        impact.transitiveDependents().forEach(node ->
                addAffected(review, existing, node.object().objectName(), node.object().objectType(),
                        node.object().owner(), "TRANSITIVE_DEPENDENT"));
        impact.schedulerJobs().forEach(job ->
                addAffected(review, existing, job.jobName(), "JOB", job.owner(), "SCHEDULER_JOB"));
    }

    private static void addAffected(
            ChangeReview review,
            Set<String> existing,
            String name,
            String type,
            String owner,
            String changeKind
    ) {
        if (existing.add(key(name, changeKind))) {
            review.addAffectedObject(new AffectedObject(name, type, owner, changeKind, null));
        }
    }

    private static String key(String name, String changeKind) {
        return name + "|" + changeKind;
    }

    static String nullToUnknown(String status) {
        return status == null || status.isBlank() ? "UNKNOWN" : status;
    }
}
