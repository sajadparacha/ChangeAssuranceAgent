# Assurance Tools

Registered via `ToolRegistry` / `AssuranceTool` beans:

| ToolType | Implementation |
|----------|----------------|
| CHECK_CHANGE_PACKAGE_COMPLETENESS | CompletenessTool |
| ANALYZE_SQL_SCRIPT | SqlSafetyTool |
| COMPARE_DEPLOYMENT_AND_ROLLBACK | DeploymentRollbackComparisonTool |
| ANALYZE_TEST_EVIDENCE_COVERAGE | TestEvidenceTool |
| CHECK_CROSS_DOCUMENT_CONSISTENCY | CrossDocumentConsistencyTool |
| GET_PACKAGE_OBJECT_INFO | GetPackageObjectInfoTool |
| ANALYZE_DIRECT_DEPENDENTS | AnalyzeDirectDependentsTool |
| ANALYZE_PACKAGE_DEPENDENCIES | AnalyzePackageDependenciesTool |
| ANALYZE_TRANSITIVE_DEPENDENTS | AnalyzeTransitiveDependentsTool |
| ANALYZE_SOURCE_REFERENCES | AnalyzeSourceReferencesTool |
| ANALYZE_SCHEDULER_JOBS | AnalyzeSchedulerJobsTool |
| ANALYZE_RELATED_OBJECT_HEALTH | AnalyzeRelatedObjectHealthTool |
| ANALYZE_PACKAGE_DB_IMPACT | PackageDbImpactTool (compat aggregator; prefer discrete tools) |
| VALIDATE_EVIDENCE_REFERENCES | EvidenceValidationTool |

## Package impact investigation

Package-only reviews plan the discrete Oracle read-only tools by default. Uploaded package DDL is analyzed for spec vs body changes and can derive `packageName` when omitted. After the initial plan runs, `InvestigationLoopService` may append bounded follow-ups (max 2 rounds), typically:

- `ANALYZE_TRANSITIVE_DEPENDENTS` when direct dependents exceed the configured threshold
- `ANALYZE_SOURCE_REFERENCES` when package/view dependents or a large blast radius is present

SQL is never executed. Catalog access uses prepared SELECT statements only (`fake` in-memory catalog or optional Oracle JDBC). Application source is not scanned. Add a tool = implement interface + tests + Spring bean.
