package com.company.changeassurance.application.port.out;

import java.util.List;
import java.util.Optional;

import com.company.changeassurance.domain.model.Evidence;
import com.company.changeassurance.domain.model.EvidenceId;
import com.company.changeassurance.domain.model.ReviewId;

public interface EvidenceRepository {

    Evidence save(ReviewId reviewId, Evidence evidence);

    Optional<Evidence> findById(EvidenceId evidenceId);

    List<Evidence> findByReviewId(ReviewId reviewId);
}
