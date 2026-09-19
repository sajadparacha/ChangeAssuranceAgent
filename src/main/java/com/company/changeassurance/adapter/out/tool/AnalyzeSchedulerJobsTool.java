package com.company.changeassurance.adapter.out.tool;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.company.changeassurance.application.port.out.DatabaseMetadataPort;
import com.company.changeassurance.application.service.IdGenerator;
import com.company.changeassurance.domain.db.DbSchedulerJob;
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
public class AnalyzeSchedulerJobsTool implements AssuranceTool {

    private final IdGenerator ids;
    private final DatabaseMetadataPort databaseMetadataPort;

    public AnalyzeSchedulerJobsTool(IdGenerator ids, DatabaseMetadataPort databaseMetadataPort) {
        this.ids = ids;
        this.databaseMetadataPort = databaseMetadataPort;
    }

    @Override
    public ToolType type() {
        return ToolType.ANALYZE_SCHEDULER_JOBS;
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
                    shell == null ? "Scheduler analysis failed" : shell.summary());
        }

        try {
            List<DbSchedulerJob> jobs = databaseMetadataPort.listSchedulerJobsReferencing(
                    shell.packageInfo().owner(), shell.packageInfo().packageName());
            PackageDbImpact updated = shell.withSchedulerJobs(jobs);
            review.setPackageDbImpact(updated);
            PackageImpactToolSupport.addAffectedObjects(review, updated);

            EvidenceId summary = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "scheduler-jobs",
                    "Scheduler jobs referencing package",
                    updated.packageInfo().qualifiedName() + " jobs=" + jobs.size());

            if (!jobs.isEmpty()) {
                findings.add(FindingFactory.deterministic(
                        ids.nextFindingId(),
                        "DBI-009",
                        "Scheduled jobs reference package",
                        updated.packageInfo().qualifiedName() + " is referenced by " + jobs.size()
                                + " scheduler job(s)",
                        FindingSeverity.HIGH,
                        FindingCategory.DEPENDENCY_IMPACT,
                        List.of(summary),
                        "Coordinate job disable/enable windows and validate post-change job runs.",
                        type()
                ));
            }
            for (DbSchedulerJob job : jobs) {
                PackageImpactToolSupport.addEvidence(
                        evidence, ids, "job:" + job.qualifiedName(),
                        "Scheduler job (" + job.jobType() + ")",
                        job.qualifiedName() + " enabled=" + job.enabled()
                                + " state=" + job.state() + " action=" + job.actionExcerpt());
            }
            return ToolExecutionResult.success(findings, evidence,
                    "Scheduler jobs=" + jobs.size() + " via " + updated.catalogMode());
        } catch (RuntimeException ex) {
            EvidenceId eid = PackageImpactToolSupport.addEvidence(
                    evidence, ids, "catalog-error", "Database metadata catalog error", ex.getMessage());
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(), "DBI-000", "Database catalog unavailable", ex.getMessage(),
                    FindingSeverity.CRITICAL, FindingCategory.DEPENDENCY_IMPACT, List.of(eid),
                    "Restore read-only catalog access or switch to fake metadata mode for local demos.",
                    type()));
            return ToolExecutionResult.success(findings, evidence, "Scheduler analysis failed: catalog unavailable");
        }
    }
}
