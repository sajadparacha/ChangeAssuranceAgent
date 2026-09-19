# AI Change Assurance Agent

An intelligent release-review agent that autonomously plans and performs a first-pass assessment of proposed production changes. It invokes controlled analysis tools, evaluates submitted evidence, identifies gaps and contradictions, creates potential failure scenarios, and produces an evidence-backed recommendation for **human approval**.

## Status

**MVP complete (Phases 1–8):** deterministic tools, REST/OpenAPI, agent workflow with clarification, AI ports, Angular UI, Docker, demo package, and documentation.

## Stack

| Layer | Choice |
|-------|--------|
| Backend | Java **17**, Spring Boot **3.5**, WAR |
| Enterprise deploy | WebLogic **15.1.1+** (Jakarta EE) |
| Frontend | Angular **19** standalone + Material |
| AI | ChatGPT or local OpenAI-compatible (Ollama/LM Studio) via `ModelGateway`; modes: `spring-ai`/`openai`, `ollama`/`local`, `fake`, `disabled` |
| Persistence | In-memory adapter (MVP) + H2 on classpath |

## Backend

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export OPENAI_API_KEY=sk-your-key-here
# optional: export CHANGEASSURANCE_AI_MODEL=gpt-4o
mvn clean verify
mvn spring-boot:run
```

Without `OPENAI_API_KEY`, the app still starts and falls back to deterministic checks (AI sections unavailable).

**Local AI (Ollama + Qwen3):**

```bash
ollama pull qwen3:14b
export CHANGEASSURANCE_AI_MODE=ollama
mvn spring-boot:run
```

See [`.env.example`](.env.example) and [`docs/ai-prompts.md`](docs/ai-prompts.md) for ChatGPT, Ollama, and LM Studio configuration.

- Health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- API: http://localhost:8080/api/v1/change-reviews
- Package impact (fake catalog): submit `packageName=BILLING_PKG`; HTML report at `/api/v1/change-reviews/{id}/report.html`

Executable WAR:

```bash
mvn clean package
java -jar target/change-assurance-agent.war
```

## Frontend

```bash
cd frontend
npm install
npm start
```

http://localhost:4200 (proxies `/api` to `:8080`).

## Docker

```bash
mvn -DskipTests package
docker compose up --build
```

## Demo package

Use artifacts under [`demo-package/`](demo-package/) — see [`docs/demo-scenario.md`](docs/demo-scenario.md).

## Documentation

- [AGENTS.md](AGENTS.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/api.md](docs/api.md)
- [docs/assurance-tools.md](docs/assurance-tools.md)
- [docs/agent-workflow.md](docs/agent-workflow.md)
- [docs/risk-policy.md](docs/risk-policy.md)
- [docs/security.md](docs/security.md)
- [docs/testing.md](docs/testing.md)
- [docs/frontend-architecture.md](docs/frontend-architecture.md)
- [docs/future-roadmap.md](docs/future-roadmap.md)

## Important guarantees

- SQL is **never** executed
- Deterministic policy owns the recommendation (AI cannot override)
- Human approval remains mandatory
- Backend works without Angular; Angular is a replaceable client
