package com.company.changeassurance.domain.model;

/**
 * Deterministic readiness recommendation. Must never be overridden by AI output.
 */
public enum ReadinessRecommendation {
    GO,
    CONDITIONAL_GO,
    NO_GO_RECOMMENDED,
    INSUFFICIENT_INFORMATION
}
