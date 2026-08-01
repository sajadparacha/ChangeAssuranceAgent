package com.company.changeassurance.adapter.out.persistence.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.company.changeassurance.application.port.out.ActivityLogRepository;
import com.company.changeassurance.application.port.out.ChangeReviewRepository;
import com.company.changeassurance.application.port.out.EvidenceRepository;
import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.Evidence;
import com.company.changeassurance.domain.model.EvidenceId;
import com.company.changeassurance.domain.model.ReviewId;
import com.company.changeassurance.domain.model.StageTransition;
import com.company.changeassurance.domain.model.ToolActivity;

/**
 * In-memory persistence adapter suitable for MVP and tests.
 * Swap for JPA adapters without changing application services (DIP).
 */
@Repository
public class InMemoryPersistenceAdapter implements ChangeReviewRepository, EvidenceRepository, ActivityLogRepository {

    private final Map<String, ChangeReview> reviews = new ConcurrentHashMap<>();
    private final Map<String, Evidence> evidence = new ConcurrentHashMap<>();
    private final Map<String, List<ToolActivity>> activities = new ConcurrentHashMap<>();
    private final Map<String, List<StageTransition>> transitions = new ConcurrentHashMap<>();

    @Override
    public ChangeReview save(ChangeReview review) {
        reviews.put(review.getReviewId().value(), review);
        return review;
    }

    @Override
    public Optional<ChangeReview> findById(ReviewId reviewId) {
        return Optional.ofNullable(reviews.get(reviewId.value()));
    }

    @Override
    public List<ChangeReview> findAll() {
        return List.copyOf(reviews.values());
    }

    @Override
    public Evidence save(ReviewId reviewId, Evidence item) {
        evidence.put(item.evidenceId().value(), item);
        return item;
    }

    @Override
    public Optional<Evidence> findById(EvidenceId evidenceId) {
        return Optional.ofNullable(evidence.get(evidenceId.value()));
    }

    @Override
    public List<Evidence> findByReviewId(ReviewId reviewId) {
        ChangeReview review = reviews.get(reviewId.value());
        return review == null ? List.of() : review.getEvidence();
    }

    @Override
    public ToolActivity saveToolActivity(ToolActivity activity) {
        activities.computeIfAbsent(activity.reviewId().value(), k -> new ArrayList<>()).add(activity);
        return activity;
    }

    @Override
    public List<ToolActivity> findToolActivitiesByReviewId(ReviewId reviewId) {
        return List.copyOf(activities.getOrDefault(reviewId.value(), List.of()));
    }

    @Override
    public StageTransition saveStageTransition(StageTransition transition) {
        transitions.computeIfAbsent(transition.reviewId().value(), k -> new ArrayList<>()).add(transition);
        return transition;
    }

    @Override
    public List<StageTransition> findStageTransitionsByReviewId(ReviewId reviewId) {
        return List.copyOf(transitions.getOrDefault(reviewId.value(), List.of()));
    }
}
