# Agent Workflow

Orchestrated by `ChangeAssuranceWorkflowService` (ordinary Java application service).

Stages follow `ReviewStage`. Safe activity summaries are logged; hidden chain-of-thought is never exposed.

Clarification: at most one round (`WAITING_FOR_INFORMATION` → answer → resume).

## Package impact investigation loop

For package-only reviews (or reviews with a package target derived from deploy SQL):

1. Parse deploy SQL (when present) for touched packages and **spec vs body** delta
2. Plan discrete Oracle read-only tools (always when a package target is set)
3. Execute the plan
4. Examine evidence via `InvestigationLoopService` (deterministic heuristics)
5. Optionally append approved follow-up tools (max 2 rounds)
6. Assess deterministic overall impact + regression test scope (catalog + deploy delta; AI cannot override level)
7. Assemble JSON report and optional HTML (`/report.html`)
