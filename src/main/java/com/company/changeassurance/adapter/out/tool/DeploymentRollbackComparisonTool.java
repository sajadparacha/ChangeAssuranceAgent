package com.company.changeassurance.adapter.out.tool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
public class DeploymentRollbackComparisonTool implements AssuranceTool {

    private final IdGenerator ids;

    public DeploymentRollbackComparisonTool(IdGenerator ids) {
        this.ids = ids;
    }

    @Override
    public ToolType type() {
        return ToolType.COMPARE_DEPLOYMENT_AND_ROLLBACK;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        ChangeReviewContext ctx = request.context();
        List<Finding> findings = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        String deploy = nullToEmpty(ctx.deploymentPlan()).toLowerCase(Locale.ROOT);
        String rollback = nullToEmpty(ctx.rollbackPlan()).toLowerCase(Locale.ROOT);
        String app = nullToEmpty(ctx.review().getApplicationName()).toLowerCase(Locale.ROOT);
        String env = nullToEmpty(ctx.review().getTargetEnvironment()).toLowerCase(Locale.ROOT);

        if (deploy.isBlank() && rollback.isBlank()) {
            return ToolExecutionResult.success(List.of(), List.of(),
                    "Deployment/rollback plans were not provided; comparison skipped.");
        }

        for (AffectedObject obj : ctx.affectedObjects()) {
            String name = obj.objectName().toLowerCase(Locale.ROOT);
            if (!deploy.isBlank() && !deploy.contains(name)) {
                add(findings, evidence, "RBK-004", "Changed object absent from deployment plan",
                        FindingSeverity.HIGH, FindingCategory.DEPLOYMENT,
                        "Object " + obj.objectName() + " is modified in SQL but not mentioned in the deployment plan.",
                        "Include " + obj.objectName() + " in deployment instructions.");
            }
            if (!rollback.isBlank() && !rollback.contains(name)) {
                add(findings, evidence, "RBK-001", "Changed object absent from rollback plan",
                        FindingSeverity.CRITICAL, FindingCategory.ROLLBACK,
                        "Object " + obj.objectName() + " lacks rollback coverage.",
                        "Add rollback steps for " + obj.objectName() + ".");
            }
        }

        if (!rollback.isBlank() && !containsAny(rollback, "verif", "validat", "check")) {
            add(findings, evidence, "RBK-002", "Rollback verification missing",
                    FindingSeverity.HIGH, FindingCategory.ROLLBACK,
                    "Rollback plan does not describe verification.",
                    "Add rollback verification steps.");
        }

        if (!env.isBlank() && !rollback.isBlank() && !rollback.contains(env)
                && containsEnvToken(rollback) && !rollback.contains(env)) {
            add(findings, evidence, "RBK-003", "Rollback environment mismatch",
                    FindingSeverity.CRITICAL, FindingCategory.ROLLBACK,
                    "Rollback plan appears to reference a different environment than " + ctx.review().getTargetEnvironment(),
                    "Align rollback environment with the target environment.");
        }

        if (!app.isBlank() && !deploy.isBlank() && !deploy.contains(app) && deploy.contains("application")) {
            // soft signal only when deployment mentions another application pattern — skipped if app present
        }

        return ToolExecutionResult.success(findings, evidence,
                "Deployment/rollback comparison produced " + findings.size() + " finding(s).");
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

    private static boolean containsAny(String text, String... tokens) {
        for (String t : tokens) {
            if (text.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsEnvToken(String text) {
        return containsAny(text, "prod", "production", "uat", "sit", "dev", "environment");
    }
}
