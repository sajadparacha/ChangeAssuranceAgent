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
public class TestEvidenceTool implements AssuranceTool {

    private final IdGenerator ids;

    public TestEvidenceTool(IdGenerator ids) {
        this.ids = ids;
    }

    @Override
    public ToolType type() {
        return ToolType.ANALYZE_TEST_EVIDENCE_COVERAGE;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        ChangeReviewContext ctx = request.context();
        List<Finding> findings = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        String tests = nullToEmpty(ctx.testEvidence()).toLowerCase(Locale.ROOT);

        if (tests.isBlank()) {
            return ToolExecutionResult.success(List.of(), List.of(),
                    "Test evidence was not provided; coverage analysis skipped.");
        }

        for (AffectedObject obj : ctx.affectedObjects()) {
            if (!tests.contains(obj.objectName().toLowerCase(Locale.ROOT))) {
                add(findings, evidence, "TST-001", "Test evidence missing affected object", FindingSeverity.HIGH,
                        "Test evidence does not reference " + obj.objectName() + ".",
                        "Add tests covering " + obj.objectName() + ".");
            }
        }

        if (!containsAny(tests, "negative", "failure", "error path", "invalid")) {
            add(findings, evidence, "TST-002", "Negative testing not described", FindingSeverity.MEDIUM,
                    "No negative testing is described.", "Describe negative test cases.");
        }
        if (!containsAny(tests, "rollback")) {
            add(findings, evidence, "TST-003", "Rollback testing not described", FindingSeverity.HIGH,
                    "Rollback testing is not described.", "Describe rollback test execution.");
        }
        if (tests.contains("all tests passed") && tests.length() < 80) {
            add(findings, evidence, "TST-004", "Unsupported test claim lacks detail", FindingSeverity.MEDIUM,
                    "Claim that all tests passed lacks supporting detail.",
                    "Provide case IDs, components covered, and outcomes.");
        }

        return ToolExecutionResult.success(findings, evidence,
                "Test evidence analysis produced " + findings.size() + " finding(s).");
    }

    private void add(
            List<Finding> findings,
            List<Evidence> evidence,
            String code,
            String title,
            FindingSeverity severity,
            String description,
            String action
    ) {
        EvidenceId eid = new EvidenceId(ids.nextEvidenceId());
        evidence.add(new Evidence(
                eid, EvidenceType.TOOL_RESULT, EvidenceSource.DETERMINISTIC_TOOL, code, title, description,
                null, null, Instant.now()
        ));
        findings.add(FindingFactory.deterministic(
                ids.nextFindingId(), code, title, description, severity, FindingCategory.TESTING,
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
}
