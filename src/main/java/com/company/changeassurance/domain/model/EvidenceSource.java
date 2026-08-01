package com.company.changeassurance.domain.model;

/**
 * Evidence provenance source categories.
 */
public enum EvidenceSource {
    SUBMITTED_PACKAGE,
    UPLOADED_FILE,
    DETERMINISTIC_TOOL,
    DETERMINISTIC_RULE,
    USER_CLARIFICATION,
    POLICY_ENGINE,
    AI_OUTPUT,
    SYSTEM
}
