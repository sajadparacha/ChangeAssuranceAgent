# Architecture — AI Change Assurance Agent

## Overview

Modular monolith using Clean Architecture and ports-and-adapters.

```text
Inbound adapters (REST / future CLI / future OpenWebUI)
        |
        v
Application use cases and input ports
        |
        v
Domain model, rules, policy interfaces
        ^
        |
Outbound ports (ModelGateway, repositories, file storage, SQL analysis, …)
        ^
        |
Outbound adapters (AI, persistence, file, clock, …)
```

## Package structure

```text
com.company.changeassurance
├── domain          # Framework-free models, rules, policy contracts, exceptions
├── application     # Use-case ports, commands/results, future workflow services
├── adapter
│   ├── in.web      # REST (Phase 3); ApiError model skeleton in Phase 1
│   └── out.*       # AI, persistence, file, clock, sql, report
└── configuration   # Spring wiring only
```

## SOLID decisions (Phase 1)

| Principle | Decision |
|-----------|----------|
| SRP | Separate types for classification, plan, evidence, findings, risk, tools, gateways |
| OCP | `ChangeReviewRule` and `AssuranceTool` extension points defined before implementations |
| LSP | `ModelGateway` with Fake / Disabled (Spring AI later) |
| ISP | Focused ports (`ChangeReviewRepository`, `EvidenceRepository`, `ClockPort`, …) |
| DIP | Application depends on ports; adapters implement ports |

## Deterministic vs AI responsibilities

| Deterministic (Java) | AI (controlled tasks only) |
|----------------------|----------------------------|
| SQL parse / safety patterns | Change classification |
| Completeness checks | Review planning (validated) |
| Rollback / test / consistency tools | Gap questions, risk hypotheses |
| Risk score and recommendation | Draft report, critic pass |
| Evidence ID validation | Explanations citing evidence |

## Deployment modes

### Executable WAR (local / Docker)

```bash
mvn clean package
java -jar target/change-assurance-agent.war
```

Embedded Tomcat is included in the Spring Boot executable WAR even though `spring-boot-starter-tomcat` is `provided` for external containers.

### WebLogic 15.1.1+ (Java 17, Jakarta EE)

1. Build: `mvn clean package`
2. Deploy `target/change-assurance-agent.war`
3. Container supplies the servlet API (`ChangeAssuranceServletInitializer` boots the app)

**Assumption:** WebLogic **15.1.1+** with Jakarta namespace. WebLogic 14.1.1 (`javax`) is not a supported target for Spring Boot 3.

Spring Boot **3.5.x** was chosen over Boot 4.x for Java 17 + WebLogic 15 alignment and forthcoming Spring AI compatibility.

## Persistence (Phase 1)

- H2 in-memory datasource configured
- JPA on classpath; `ddl-auto: none`
- No JPA entities yet — domain models stay pure
- Entities and repositories arrive with Phase 2/3 lifecycle persistence

## AI gateway

```text
ModelGateway (port)
├── FakeModelGateway      # tests / local stubs
├── DisabledModelGateway  # AI off; AiUnavailableException
└── SpringAiModelGateway  # OpenAI-compatible: ChatGPT, Ollama, LM Studio
```

When AI is unavailable, the workflow still runs deterministic checks and marks AI report sections `UNAVAILABLE`.

## Package impact analysis (Oracle facts layer)

```text
DatabaseMetadataPort
├── FakeDatabaseMetadataAdapter   # demo catalog
└── OracleJdbcMetadataAdapter     # read-only ALL_* dictionary SELECTs
```

Discrete approved tools gather object info, direct/transitive dependents, dependencies, source references, scheduler jobs, and related object health. `InvestigationLoopService` may append up to two follow-up tool rounds. `PackageImpactAssessmentCalculator` sets deterministic overall impact (`LOW|MEDIUM|HIGH`) and regression test scope; AI may explain but must not override the level.

## Security posture (foundation)

- Architecture is Spring Security-ready (no auth filter chain yet)
- Standard `ApiError` shape defined for Phase 3
- No secrets in repository
- Uploads and SQL execution controls arrive with file storage and tools

## Phase 1 boundaries

Included: Maven project, domain model, ports, Fake/Disabled AI, clock, H2/Actuator, WAR bootstrap, ArchUnit, foundation docs.

Excluded: deterministic tools, SQL parser, REST submit API, Angular, live AI, workflow orchestration, Docker, golden scenarios.

## Next phase

**Phase 2 — Evidence and Deterministic Tools:** safe file storage, evidence catalog, SQL parser, completeness / SQL safety / rollback / test / consistency tools, risk engine, recommendation policy.


## Implementation status

Phases 1–8 delivered in MVP form: deterministic tools, REST/OpenAPI, agent workflow, AI ports (fake/disabled/spring-ai stub), Angular client, Docker, demo package, and documentation set.
