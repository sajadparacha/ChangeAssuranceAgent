# AI Change Assurance Agent — Agent Guide

## Product

**AI Change Assurance Agent** is an intelligent release-review agent that autonomously plans and performs a first-pass assessment of proposed production changes. It invokes controlled analysis tools, evaluates submitted evidence, identifies gaps and contradictions, creates potential failure scenarios, and produces an evidence-backed recommendation for human approval.

It is **not** merely an SQL checker.

## Standalone design

The application is a standalone modular monolith. It must work without OpenWebUI, Angular (backend-first), a live LLM provider, production databases, Git, CI/CD, or ticketing systems.

OpenWebUI and Angular are **replaceable clients** of a stable REST/OpenAPI contract (Phase 3+).

## Runtime targets

| Mode | Target |
|------|--------|
| Local / Docker | Executable WAR (`java -jar`) with embedded Tomcat |
| Enterprise | WAR on **WebLogic 15.1.1+** (Jakarta EE), **Java 17** |

## Architecture rules for contributors

1. **Dependency direction:** adapters → application → domain. Domain never depends on Spring, JPA, REST, H2, or model SDKs.
2. **Deterministic vs AI:** SQL safety, completeness, risk score, and recommendation are deterministic. AI classifies, plans, explains, hypothesizes, and drafts — never overrides policy.
3. **One primary agent:** Change Assurance Agent with controlled stages and an approved tool registry.
4. **No hidden chain-of-thought** in logs or APIs — only safe action summaries.
5. **SQL is never executed.**
6. **Human approval is always mandatory.**
7. **Extend via new rules/tools** (`ChangeReviewRule`, `AssuranceTool`) + tests + bean registration — do not enlarge the workflow monolithically.
8. **ModelGateway** implementations must be interchangeable: Fake, Disabled, Spring AI (Phase 4).

## Controlled workflow stages

```text
RECEIVED → VALIDATING_INPUT → CREATING_EVIDENCE → CLASSIFYING_CHANGE
→ PLANNING_REVIEW → EXECUTING_TOOLS → ANALYZING_EVIDENCE
→ WAITING_FOR_INFORMATION (optional) → GENERATING_RISK_SCENARIOS
→ GENERATING_DRAFT_REPORT → CRITIC_REVIEW → CALCULATING_RECOMMENDATION
→ COMPLETED | FAILED
```

## Implementation phases

| Phase | Scope | Status |
|-------|--------|--------|
| 1 | Foundation | Complete |
| 2 | Evidence and deterministic tools | Complete |
| 3 | REST API | Complete |
| 4 | AI classification and planning | Complete |
| 5 | Tool-using agent workflow | Complete |
| 6 | AI reasoning and critic | Complete |
| 7 | Angular frontend | Complete |
| 8 | Hardening | Complete |

**All MVP phases are complete.**

## AI modes (Phase 1)

Property: `changeassurance.ai.mode`

| Value | Behavior |
|-------|----------|
| `fake` | `FakeModelGateway` — stub responses for tests/local |
| `disabled` | `DisabledModelGateway` — throws `AiUnavailableException`; review must still be able to complete deterministically |
| `spring-ai` / `openai` / `chatgpt` | `SpringAiModelGateway` — ChatGPT via `OPENAI_API_KEY` (fails closed if key missing) |
| `ollama` / `local` | `SpringAiModelGateway` — OpenAI-compatible local server (default Ollama + `qwen3:14b` at `http://localhost:11434/v1`); clients select provider+model via `GET /api/v1/ai/config` and optional `aiProvider`/`aiModel` on submit |

When mode is `spring-ai`/`openai`/`chatgpt`, Ollama is also offered as a selectable provider (default on) via `changeassurance.ai.ollama-enabled` / `CHANGEASSURANCE_AI_OLLAMA_ENABLED`, discovering models from `changeassurance.ai.ollama-base-url`.

Package DB impact: submit with `packageName` (optional `schemaOwner`), or upload Oracle package DDL/SQL and the agent derives the package target. Catalog metadata is queried via discrete read-only tools (`GET_PACKAGE_OBJECT_INFO`, dependents/dependencies, scheduler, health; follow-ups may add transitive + source search). Spec vs body changes in the deploy script raise deterministic impact (spec = API contract risk). Catalog modes: `oracle` (default — live JDBC dictionary tools) or optional `fake` demo catalog for offline/tests. Application source repos are not scanned. Deterministic overall impact + regression test scope cannot be overridden by AI. HTML report: `GET /api/v1/change-reviews/{id}/report.html`.

## Adding a rule (later)

1. Implement `ChangeReviewRule`
2. Add unit tests
3. Register as a Spring bean

## Adding a tool (later)

1. Implement `AssuranceTool` for an existing `ToolType` (or add enum value + registry entry)
2. Add unit tests
3. Register as a Spring bean
4. Ensure plan validation rejects unknown tools

## Security reminders

- Treat uploads as untrusted data
- Never commit secrets
- Never return stack traces to API clients
- Redact credentials; do not send secrets to models
