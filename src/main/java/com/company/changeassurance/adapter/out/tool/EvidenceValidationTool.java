package com.company.changeassurance.adapter.out.tool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.company.changeassurance.application.service.IdGenerator;
import com.company.changeassurance.domain.model.Evidence;
import com.company.changeassurance.domain.model.EvidenceId;
import com.company.changeassurance.domain.model.EvidenceSource;
import com.company.changeassurance.domain.model.EvidenceType;
import com.company.changeassurance.domain.model.Finding;
import com.company.changeassurance.domain.model.FindingCategory;
import com.company.changeassurance.domain.model.FindingSeverity;
import com.company.changeassurance.domain.model.RiskScenario;
import com.company.changeassurance.domain.model.ToolType;
import com.company.changeassurance.domain.rule.AssuranceTool;
import com.company.changeassurance.domain.rule.ToolExecutionRequest;
import com.company.changeassurance.domain.rule.ToolExecutionResult;
import com.company.changeassurance.domain.service.FindingFactory;

@Component
public class EvidenceValidationTool implements AssuranceTool {

    private final IdGenerator ids;

    public EvidenceValidationTool(IdGenerator ids) {
        this.ids = ids;
    }

    @Override
    public ToolType type() {
        return ToolType.VALIDATE_EVIDENCE_REFERENCES;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        List<Finding> findings = new ArrayList<>();
        List<Evidence> evidenceOut = new ArrayList<>();
        Set<String> known = new HashSet<>();
        for (Evidence e : request.context().evidenceCatalog()) {
            known.add(e.evidenceId().value());
        }
        for (Evidence e : request.context().review().getEvidence()) {
            known.add(e.evidenceId().value());
        }

        for (Finding finding : request.context().review().getFindings()) {
            if (finding.deterministic() && finding.evidenceIds().isEmpty()) {
                add(findings, evidenceOut, "EVD-001", "Deterministic finding lacks evidence",
                        FindingSeverity.HIGH, "Finding " + finding.findingId() + " has no evidence IDs.");
            }
            for (EvidenceId eid : finding.evidenceIds()) {
                if (!known.contains(eid.value())) {
                    add(findings, evidenceOut, "EVD-002", "Unknown evidence ID referenced",
                            FindingSeverity.HIGH, "Finding references unknown evidence " + eid.value());
                }
            }
        }

        for (RiskScenario scenario : request.context().review().getRiskScenarios()) {
            for (EvidenceId eid : scenario.supportingEvidenceIds()) {
                if (!known.contains(eid.value())) {
                    add(findings, evidenceOut, "EVD-003", "Risk scenario references unknown evidence",
                            FindingSeverity.MEDIUM, "Scenario " + scenario.scenarioId() + " refs " + eid.value());
                }
            }
        }

        return ToolExecutionResult.success(findings, evidenceOut,
                "Evidence validation produced " + findings.size() + " finding(s).");
    }

    private void add(
            List<Finding> findings,
            List<Evidence> evidence,
            String code,
            String title,
            FindingSeverity severity,
            String description
    ) {
        EvidenceId eid = new EvidenceId(ids.nextEvidenceId());
        evidence.add(new Evidence(
                eid, EvidenceType.TOOL_RESULT, EvidenceSource.DETERMINISTIC_TOOL, code, title, description,
                null, null, Instant.now()
        ));
        findings.add(FindingFactory.deterministic(
                ids.nextFindingId(), code, title, description, severity, FindingCategory.CONSISTENCY,
                List.of(eid), "Correct evidence references.", type()
        ));
    }
}
