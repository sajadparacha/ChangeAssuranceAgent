# Agent Workflow

Orchestrated by `ChangeAssuranceWorkflowService` (ordinary Java application service).

Stages follow `ReviewStage`. Safe activity summaries are logged; hidden chain-of-thought is never exposed.

Clarification: at most one round (`WAITING_FOR_INFORMATION` → answer → resume).
