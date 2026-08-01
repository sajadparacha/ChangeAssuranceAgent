package com.company.changeassurance.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Safe, auditable AI assessment content. Must not contain hidden chain-of-thought.
 */
public record AiAssessment(
        String availabilityStatus,
        String executiveSummary,
        String changeUnderstanding,
        String riskExplanation,
        List<String> suggestedTests,
        List<String> humanReviewQuestions,
        List<EvidenceId> citedEvidenceIds,
        String modelIdentifier,
        String promptVersion
) {

    public static final String UNAVAILABLE = "UNAVAILABLE";

    public AiAssessment {
        Objects.requireNonNull(availabilityStatus, "availabilityStatus must not be null");
        suggestedTests = List.copyOf(Objects.requireNonNullElse(suggestedTests, List.of()));
        humanReviewQuestions = List.copyOf(Objects.requireNonNullElse(humanReviewQuestions, List.of()));
        citedEvidenceIds = List.copyOf(Objects.requireNonNullElse(citedEvidenceIds, List.of()));
    }

    public static AiAssessment unavailable() {
        return new AiAssessment(
                UNAVAILABLE,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null
        );
    }
}
