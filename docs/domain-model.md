# Domain Model

Core aggregates and value objects live under `com.company.changeassurance.domain.model`.

- `ChangeReview` — aggregate root for a review lifecycle
- `ChangeClassification`, `ReviewPlan`, `Evidence`, `Finding`, `InformationGap`
- `RiskScenario` (always hypothesis), `RiskAssessment`, `ToolActivity`, `StageTransition`
- Recommendations: `GO`, `CONDITIONAL_GO`, `NO_GO_RECOMMENDED`, `INSUFFICIENT_INFORMATION`

Domain code has **no** Spring/JPA/REST dependencies (enforced by ArchUnit).
