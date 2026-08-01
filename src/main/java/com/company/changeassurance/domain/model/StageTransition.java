package com.company.changeassurance.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Persisted stage transition for auditability.
 */
public record StageTransition(
        ReviewId reviewId,
        ReviewStage previousStage,
        ReviewStage newStage,
        Instant timestamp,
        String reason,
        ActorType actorType,
        List<ActivityId> relatedToolExecutionIds,
        List<EvidenceId> relatedEvidenceIds
) {

    public StageTransition {
        Objects.requireNonNull(reviewId, "reviewId must not be null");
        Objects.requireNonNull(newStage, "newStage must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        relatedToolExecutionIds = List.copyOf(
                Objects.requireNonNull(relatedToolExecutionIds, "relatedToolExecutionIds must not be null"));
        relatedEvidenceIds = List.copyOf(
                Objects.requireNonNull(relatedEvidenceIds, "relatedEvidenceIds must not be null"));
    }
}
