package com.company.changeassurance.adapter.out.tool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.company.changeassurance.application.service.IdGenerator;
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
public class CompletenessTool implements AssuranceTool {

    private final IdGenerator ids;

    public CompletenessTool(IdGenerator ids) {
        this.ids = ids;
    }

    @Override
    public ToolType type() {
        return ToolType.CHECK_CHANGE_PACKAGE_COMPLETENESS;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        ChangeReviewContext ctx = request.context();
        List<Finding> findings = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();

        check(findings, evidence, "PKG-001", "Missing change description", FindingSeverity.CRITICAL,
                isBlank(ctx.review().getChangeDescription()),
                "Provide a clear change description.", request);
        check(findings, evidence, "PKG-002", "Missing deployment plan", FindingSeverity.CRITICAL,
                isBlank(ctx.deploymentPlan()),
                "Provide a deployment plan describing order and steps.", request);
        check(findings, evidence, "PKG-003", "Missing rollback plan", FindingSeverity.CRITICAL,
                isBlank(ctx.rollbackPlan()),
                "Provide a rollback plan covering all modified objects.", request);
        check(findings, evidence, "PKG-004", "Missing test evidence", FindingSeverity.HIGH,
                isBlank(ctx.testEvidence()),
                "Provide test evidence covering affected components.", request);
        check(findings, evidence, "PKG-007", "Target environment not specified", FindingSeverity.HIGH,
                isBlank(ctx.review().getTargetEnvironment()),
                "Specify the target environment.", request);
        check(findings, evidence, "PKG-008", "Execution responsibility not specified", FindingSeverity.MEDIUM,
                !containsAny(ctx.deploymentPlan(), "responsible", "owner", "dba", "deployer", "team"),
                "Identify who will execute the deployment.", request);
        check(findings, evidence, "PKG-005", "Missing deployment verification", FindingSeverity.HIGH,
                !containsAny(ctx.deploymentPlan(), "verif", "validat", "check", "smoke"),
                "Add post-deployment verification steps.", request);
        check(findings, evidence, "PKG-006", "Missing rollback verification", FindingSeverity.HIGH,
                !containsAny(ctx.rollbackPlan(), "verif", "validat", "check"),
                "Add rollback verification steps.", request);

        boolean orderDescribed = containsAny(ctx.deploymentPlan(), "order", "sequence", "step 1", "first", "then");
        if (!isBlank(ctx.deploymentPlan()) && !orderDescribed) {
            check(findings, evidence, "PKG-009", "Deployment order not described", FindingSeverity.MEDIUM,
                    true, "Describe deployment order explicitly.", request);
        }

        return ToolExecutionResult.success(
                findings,
                evidence,
                "Completeness analysis produced " + findings.size() + " finding(s)."
        );
    }

    private void check(
            List<Finding> findings,
            List<Evidence> evidence,
            String code,
            String title,
            FindingSeverity severity,
            boolean triggered,
            String action,
            ToolExecutionRequest request
    ) {
        if (!triggered) {
            return;
        }
        EvidenceId eid = new EvidenceId(ids.nextEvidenceId());
        evidence.add(new Evidence(
                eid,
                EvidenceType.DETERMINISTIC_RULE_RESULT,
                EvidenceSource.DETERMINISTIC_RULE,
                code,
                title + " triggered",
                title,
                null,
                null,
                Instant.now()
        ));
        findings.add(FindingFactory.deterministic(
                ids.nextFindingId(),
                code,
                title,
                title,
                severity,
                FindingCategory.COMPLETENESS,
                List.of(eid),
                action,
                type()
        ));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean containsAny(String text, String... tokens) {
        if (isBlank(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
