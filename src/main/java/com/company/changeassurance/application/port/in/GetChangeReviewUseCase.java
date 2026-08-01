package com.company.changeassurance.application.port.in;

import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.ReviewId;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for retrieving reviews. Implementation arrives with the REST API (Phase 3).
 */
public interface GetChangeReviewUseCase {

    Optional<ChangeReview> getById(ReviewId reviewId);

    List<ChangeReview> listReviews();
}
