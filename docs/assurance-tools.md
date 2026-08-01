# Assurance Tools

Registered via `ToolRegistry` / `AssuranceTool` beans:

| ToolType | Implementation |
|----------|----------------|
| CHECK_CHANGE_PACKAGE_COMPLETENESS | CompletenessTool |
| ANALYZE_SQL_SCRIPT | SqlSafetyTool |
| COMPARE_DEPLOYMENT_AND_ROLLBACK | DeploymentRollbackComparisonTool |
| ANALYZE_TEST_EVIDENCE_COVERAGE | TestEvidenceTool |
| CHECK_CROSS_DOCUMENT_CONSISTENCY | CrossDocumentConsistencyTool |
| VALIDATE_EVIDENCE_REFERENCES | EvidenceValidationTool |

SQL is never executed. Add a tool = implement interface + tests + Spring bean.
