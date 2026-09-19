package com.company.changeassurance.adapter.out.tool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.company.changeassurance.application.service.IdGenerator;
import com.company.changeassurance.domain.model.AffectedObject;
import com.company.changeassurance.domain.model.Evidence;
import com.company.changeassurance.domain.model.EvidenceId;
import com.company.changeassurance.domain.model.EvidenceSource;
import com.company.changeassurance.domain.model.EvidenceType;
import com.company.changeassurance.domain.model.Finding;
import com.company.changeassurance.domain.model.FindingCategory;
import com.company.changeassurance.domain.model.FindingSeverity;
import com.company.changeassurance.domain.model.ToolType;
import com.company.changeassurance.domain.rule.AssuranceTool;
import com.company.changeassurance.domain.rule.ChangeReviewRule.ChangeReviewContext;
import com.company.changeassurance.domain.rule.ToolExecutionRequest;
import com.company.changeassurance.domain.rule.ToolExecutionResult;
import com.company.changeassurance.domain.service.FindingFactory;

@Component
public class CrossDocumentConsistencyTool implements AssuranceTool {

    private final IdGenerator ids;

    public CrossDocumentConsistencyTool(IdGenerator ids) {
        this.ids = ids;
    }

    @Override
    public ToolType type() {
        return ToolType.CHECK_CROSS_DOCUMENT_CONSISTENCY;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        ChangeReviewContext ctx = request.context();
        List<Finding> findings = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();

        String description = nullToEmpty(ctx.review().getChangeDescription()).toLowerCase(Locale.ROOT);
        String deploy = nullToEmpty(ctx.deploymentPlan()).toLowerCase(Locale.ROOT);
        String rollback = nullToEmpty(ctx.rollbackPlan()).toLowerCase(Locale.ROOT);
        String tests = nullToEmpty(ctx.testEvidence()).toLowerCase(Locale.ROOT);
        String app = nullToEmpty(ctx.review().getApplicationName()).toLowerCase(Locale.ROOT);
        String env = nullToEmpty(ctx.review().getTargetEnvironment()).toLowerCase(Locale.ROOT);

        boolean understated = description.contains("minor") || description.contains("body only")
                || description.contains("package body");
        if (understated && ctx.affectedObjects().size() > 1) {
            add(findings, evidence, "CON-003", "Change description understates scope", FindingSeverity.HIGH,
                    FindingCategory.SCOPE,
                    "Description suggests a narrow change but SQL affects multiple objects: "
                            + ctx.affectedObjects().stream().map(AffectedObject::objectName).toList(),
                    "Update the change description to reflect full scope.");
        }

        for (AffectedObject obj : ctx.affectedObjects()) {
            String name = obj.objectName().toLowerCase(Locale.ROOT);
            if (!deploy.isBlank() && !deploy.contains(name)) {
                add(findings, evidence, "CON-004", "Deployment and SQL object mismatch", FindingSeverity.HIGH,
                        FindingCategory.CONSISTENCY,
                        "Deployment plan omits SQL-modified object " + obj.objectName() + ".",
                        "Align deployment plan with SQL scope.");
            }
        }

        if (!app.isBlank()) {
            if ((!deploy.isBlank() && deploy.contains("application") && !deploy.contains(app))
                    || (!rollback.isBlank() && rollback.contains("application") && !rollback.contains(app))) {
                add(findings, evidence, "CON-001", "Application-name mismatch", FindingSeverity.HIGH,
                        FindingCategory.CONSISTENCY,
                        "Application name in plans may not match submitted application " + ctx.review().getApplicationName(),
                        "Ensure application names are consistent.");
            }
        }

        if (!env.isBlank() && !deploy.isBlank() && containsEnvToken(deploy) && !deploy.contains(env)) {
            add(findings, evidence, "CON-002", "Environment mismatch", FindingSeverity.CRITICAL,
                    FindingCategory.CONSISTENCY,
                    "Deployment plan environment appears inconsistent with target " + ctx.review().getTargetEnvironment(),
                    "Align environment names across artifacts.");
        }

        if (!tests.isBlank()) {
            for (AffectedObject obj : ctx.affectedObjects()) {
                if (("TABLE".equalsIgnoreCase(obj.objectType()) || "PACKAGE_SPEC".equalsIgnoreCase(obj.objectType()))
                        && !tests.contains(obj.objectName().toLowerCase(Locale.ROOT))) {
                    add(findings, evidence, "CON-005", "Test evidence ignores affected component", FindingSeverity.MEDIUM,
                            FindingCategory.CONSISTENCY,
                            "Tests ignore affected object " + obj.objectName() + ".",
                            "Extend tests to cover " + obj.objectName() + ".");
                }
            }
        }

        if (!deploy.isBlank() && !rollback.isBlank()
                && deploy.contains("package body") && !rollback.contains("specification")
                && ctx.affectedObjects().stream().anyMatch(o -> "PACKAGE_SPEC".equalsIgnoreCase(o.objectType()))) {
            add(findings, evidence, "CON-006", "Contradictory deployment and rollback steps", FindingSeverity.HIGH,
                    FindingCategory.CONSISTENCY,
                    "Deployment/rollback narrative omits package specification recovery while SQL changes it.",
                    "Reconcile deployment and rollback for package specification.");
        }

        return ToolExecutionResult.success(findings, evidence,
                "Cross-document consistency produced " + findings.size() + " finding(s).");
    }

    private void add(
            List<Finding> findings,
            List<Evidence> evidence,
            String code,
            String title,
            FindingSeverity severity,
            FindingCategory category,
            String description,
            String action
    ) {
        EvidenceId eid = new EvidenceId(ids.nextEvidenceId());
        evidence.add(new Evidence(
                eid, EvidenceType.TOOL_RESULT, EvidenceSource.DETERMINISTIC_TOOL, code, title, description,
                null, null, Instant.now()
        ));
        findings.add(FindingFactory.deterministic(
                ids.nextFindingId(), code, title, description, severity, category,
                List.of(eid), action, type()
        ));
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static boolean containsEnvToken(String text) {
        return text.contains("prod") || text.contains("uat") || text.contains("sit")
                || text.contains("dev") || text.contains("environment");
    }
}
