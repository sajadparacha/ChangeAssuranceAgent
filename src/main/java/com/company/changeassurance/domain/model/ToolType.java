package com.company.changeassurance.domain.model;

/**
 * Approved deterministic and validation tool types. AI must not invent tools outside this set.
 */
public enum ToolType {
    CHECK_CHANGE_PACKAGE_COMPLETENESS,
    ANALYZE_SQL_SCRIPT,
    COMPARE_DEPLOYMENT_AND_ROLLBACK,
    ANALYZE_TEST_EVIDENCE_COVERAGE,
    CHECK_CROSS_DOCUMENT_CONSISTENCY,
    /** @deprecated Prefer discrete package impact tools; retained for plan compatibility. */
    ANALYZE_PACKAGE_DB_IMPACT,
    GET_PACKAGE_OBJECT_INFO,
    ANALYZE_DIRECT_DEPENDENTS,
    ANALYZE_PACKAGE_DEPENDENCIES,
    ANALYZE_TRANSITIVE_DEPENDENTS,
    ANALYZE_SOURCE_REFERENCES,
    ANALYZE_SCHEDULER_JOBS,
    ANALYZE_RELATED_OBJECT_HEALTH,
    CALCULATE_DETERMINISTIC_RISK,
    VALIDATE_EVIDENCE_REFERENCES
}
