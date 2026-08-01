package com.company.changeassurance.domain.model;

import java.util.List;
import java.util.Objects;

public record ReviewPlan(
        PlanId planId,
        ReviewId reviewId,
        List<ReviewPlanStep> orderedSteps,
        List<ToolType> requiredTools,
        List<String> stepReasons,
        PlanStatus planStatus,
        int revisionNumber,
        ActorType createdByActor,
        List<EvidenceId> evidenceIdsThatCausedPlanChanges
) {

    public ReviewPlan {
        Objects.requireNonNull(planId, "planId must not be null");
        Objects.requireNonNull(reviewId, "reviewId must not be null");
        orderedSteps = List.copyOf(Objects.requireNonNull(orderedSteps, "orderedSteps must not be null"));
        requiredTools = List.copyOf(Objects.requireNonNull(requiredTools, "requiredTools must not be null"));
        stepReasons = List.copyOf(Objects.requireNonNull(stepReasons, "stepReasons must not be null"));
        Objects.requireNonNull(planStatus, "planStatus must not be null");
        if (revisionNumber < 1) {
            throw new IllegalArgumentException("revisionNumber must be >= 1");
        }
        Objects.requireNonNull(createdByActor, "createdByActor must not be null");
        evidenceIdsThatCausedPlanChanges = List.copyOf(Objects.requireNonNull(
                evidenceIdsThatCausedPlanChanges, "evidenceIdsThatCausedPlanChanges must not be null"));
    }
}
