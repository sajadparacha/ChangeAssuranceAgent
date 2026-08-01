package com.company.changeassurance.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Human answer to a single clarification round.
 */
public record ClarificationAnswer(
        GapId gapId,
        String question,
        String answer,
        Instant answeredAt,
        EvidenceId storedAsEvidenceId
) {

    public ClarificationAnswer {
        Objects.requireNonNull(gapId, "gapId must not be null");
        Objects.requireNonNull(question, "question must not be null");
        Objects.requireNonNull(answer, "answer must not be null");
        Objects.requireNonNull(answeredAt, "answeredAt must not be null");
    }
}
