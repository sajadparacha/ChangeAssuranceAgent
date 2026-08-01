package com.company.changeassurance.domain.model;

/**
 * Controlled agent workflow stages. Every transition must be persisted with audit metadata.
 */
public enum ReviewStage {
    RECEIVED,
    VALIDATING_INPUT,
    CREATING_EVIDENCE,
    CLASSIFYING_CHANGE,
    PLANNING_REVIEW,
    EXECUTING_TOOLS,
    ANALYZING_EVIDENCE,
    WAITING_FOR_INFORMATION,
    GENERATING_RISK_SCENARIOS,
    GENERATING_DRAFT_REPORT,
    CRITIC_REVIEW,
    CALCULATING_RECOMMENDATION,
    COMPLETED,
    FAILED
}
