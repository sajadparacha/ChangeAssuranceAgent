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
    CALCULATE_DETERMINISTIC_RISK,
    VALIDATE_EVIDENCE_REFERENCES
}
