# AI Change Assurance Agent

An intelligent release-review agent that autonomously plans and performs a first-pass assessment of proposed production changes. It invokes controlled analysis tools, evaluates submitted evidence, identifies gaps and contradictions, creates potential failure scenarios, and produces an evidence-backed recommendation for **human approval**.

## Stack

- Backend: Java 17, Spring Boot 3.5, WAR (local executable + WebLogic 15.x)
- Frontend: Angular 19 standalone + Angular Material
- Persistence: in-memory adapter + H2 on classpath (MVP)
- AI: `ModelGateway` with `fake` / `disabled` / `spring-ai` (fail-closed) modes

## Quick start (backend)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean verify
mvn spring-boot:run
```

- Health: http://localhost:8080/actuator/health
- Swagger: http://localhost:8080/swagger-ui.html
- API base: http://localhost:8080/api/v1

## Quick start (frontend)

```bash
cd frontend
npm install
npm start
```

Open http://localhost:4200 (proxies API to `:8080`).

## Docker

```bash
mvn -DskipTests package
docker compose up --build
```

## Demo package

See `demo-package/` and [docs/demo-scenario.md](docs/demo-scenario.md).

## Documentation

- [AGENTS.md](AGENTS.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/api.md](docs/api.md)
- [docs/assurance-tools.md](docs/assurance-tools.md)
- [docs/security.md](docs/security.md)
- [docs/testing.md](docs/testing.md)

## Human approval

The system never authoritatively approves production deployment. Recommendations are advisory and calculated by deterministic policy.
