package com.company.changeassurance.application.port.out;

import java.util.List;
import java.util.Optional;

import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.ReviewId;

public interface ChangeReviewRepository {

    ChangeReview save(ChangeReview review);

    Optional<ChangeReview> findById(ReviewId reviewId);

    List<ChangeReview> findAll();
}
