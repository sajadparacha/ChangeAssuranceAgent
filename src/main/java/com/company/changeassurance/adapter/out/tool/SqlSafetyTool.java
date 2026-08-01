package com.company.changeassurance.adapter.out.tool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
import com.company.changeassurance.domain.rule.ToolExecutionRequest;
import com.company.changeassurance.domain.rule.ToolExecutionResult;
import com.company.changeassurance.domain.service.FindingFactory;
import com.company.changeassurance.domain.sql.SqlOperationType;
import com.company.changeassurance.domain.sql.SqlParseResult;
import com.company.changeassurance.domain.sql.SqlStatementInfo;

@Component
public class SqlSafetyTool implements AssuranceTool {

    private final IdGenerator ids;

    public SqlSafetyTool(IdGenerator ids) {
        this.ids = ids;
    }

    @Override
    public ToolType type() {
        return ToolType.ANALYZE_SQL_SCRIPT;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        SqlParseResult parsed = request.context().sqlParseResult();
        List<Finding> findings = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();

        if (parsed == null) {
            return ToolExecutionResult.failure("SQL_PARSE_MISSING", "SQL parse result was not available");
        }

        for (SqlStatementInfo stmt : parsed.statements()) {
            EvidenceId eid = evidenceFor(evidence, stmt);
            if (stmt.operationType() == SqlOperationType.DROP) {
                add(findings, eid, "SQL-001", "DROP statement detected", FindingSeverity.CRITICAL, stmt);
            }
            if (stmt.operationType() == SqlOperationType.TRUNCATE) {
                add(findings, eid, "SQL-002", "TRUNCATE statement detected", FindingSeverity.CRITICAL, stmt);
            }
            if (stmt.operationType() == SqlOperationType.DELETE && stmt.missingWhere()) {
                add(findings, eid, "SQL-003", "DELETE without WHERE", FindingSeverity.CRITICAL, stmt);
            }
            if (stmt.operationType() == SqlOperationType.UPDATE && stmt.missingWhere()) {
                add(findings, eid, "SQL-004", "UPDATE without WHERE", FindingSeverity.CRITICAL, stmt);
            }
            if (stmt.operationType() == SqlOperationType.GRANT || stmt.operationType() == SqlOperationType.REVOKE) {
                add(findings, eid, "SQL-005", "Privilege change detected", FindingSeverity.HIGH, stmt);
            }
            if (stmt.operationType() == SqlOperationType.COMMIT) {
                add(findings, eid, "SQL-006", "Explicit COMMIT detected", FindingSeverity.MEDIUM, stmt);
            }
            if (stmt.potentialCredential()) {
                add(findings, eid, "SQL-007", "Potential hard-coded credential", FindingSeverity.CRITICAL, stmt);
            }
            if (stmt.operationType() == SqlOperationType.PACKAGE_SPEC) {
                add(findings, eid, "SQL-008", "Package specification changed", FindingSeverity.HIGH, stmt);
            }
            if (stmt.unsupported()) {
                add(findings, eid, "SQL-009", "Unsupported SQL construct", FindingSeverity.HIGH, stmt);
            }
        }

        if (parsed.affectedObjectNames().size() > 1) {
            EvidenceId eid = new EvidenceId(ids.nextEvidenceId());
            evidence.add(new Evidence(
                    eid,
                    EvidenceType.TOOL_RESULT,
                    EvidenceSource.DETERMINISTIC_TOOL,
                    "analyzeSqlScript",
                    "Multiple objects changed",
                    String.join(", ", parsed.affectedObjectNames()),
                    null,
                    null,
                    Instant.now()
            ));
            findings.add(FindingFactory.deterministic(
                    ids.nextFindingId(),
                    "SQL-010",
                    "Multiple objects changed",
                    "SQL script affects " + parsed.affectedObjectNames().size() + " objects: "
                            + String.join(", ", parsed.affectedObjectNames()),
                    FindingSeverity.MEDIUM,
                    FindingCategory.SQL_SAFETY,
                    List.of(eid),
                    "Confirm that the change description and plans cover all objects.",
                    type()
            ));
        }

        return ToolExecutionResult.success(
                findings,
                evidence,
                "SQL safety analysis identified " + findings.size()
                        + " finding(s) across " + parsed.affectedObjectNames().size() + " object(s)."
        );
    }

    private EvidenceId evidenceFor(List<Evidence> evidence, SqlStatementInfo stmt) {
        EvidenceId eid = new EvidenceId(ids.nextEvidenceId());
        String excerpt = stmt.rawText().length() > 240 ? stmt.rawText().substring(0, 240) + "..." : stmt.rawText();
        evidence.add(new Evidence(
                eid,
                EvidenceType.SQL_STATEMENT,
                EvidenceSource.DETERMINISTIC_TOOL,
                "line:" + stmt.startLine(),
                "SQL statement " + stmt.index() + " (" + stmt.operationType() + ")",
                excerpt,
                stmt.startLine(),
                null,
                Instant.now()
        ));
        return eid;
    }

    private void add(
            List<Finding> findings,
            EvidenceId eid,
            String code,
            String title,
            FindingSeverity severity,
            SqlStatementInfo stmt
    ) {
        findings.add(FindingFactory.deterministic(
                ids.nextFindingId(),
                code,
                title,
                title + " at line " + stmt.startLine()
                        + (stmt.unsupportedReason() != null ? ": " + stmt.unsupportedReason() : ""),
                severity,
                FindingCategory.SQL_SAFETY,
                List.of(eid),
                "Remediate or justify " + code + " before production deployment.",
                type()
        ));
    }
}
