package com.company.changeassurance.application.port.in;

import com.company.changeassurance.domain.model.ReviewId;

/**
 * Inbound port for the single clarification round. Implementation in Phase 5.
 */
public interface SubmitClarificationAnswerUseCase {

    void submit(ReviewId reviewId, SubmitClarificationAnswerCommand command);

    record SubmitClarificationAnswerCommand(
            String gapId,
            String answer
    ) {
    }
}
