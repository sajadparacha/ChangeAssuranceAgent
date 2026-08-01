package com.company.changeassurance.domain.exception;

import com.company.changeassurance.domain.model.ReviewStage;

public class InvalidStageTransitionException extends DomainException {

    public InvalidStageTransitionException(ReviewStage from, ReviewStage to, String reason) {
        super(
                "INVALID_STAGE_TRANSITION",
                "Cannot transition from " + from + " to " + to + ": " + reason
        );
    }
}
