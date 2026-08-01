package com.company.changeassurance.domain.model;

/**
 * Controlled AI reasoning tasks. Hidden chain-of-thought must never be exposed.
 */
public enum AiTaskType {
    CHANGE_CLASSIFICATION,
    REVIEW_PLANNING,
    EVIDENCE_GAP_QUESTIONING,
    CROSS_ARTIFACT_REASONING,
    RISK_SCENARIO_GENERATION,
    DRAFT_REPORT_GENERATION,
    CRITIC_REVIEW
}
