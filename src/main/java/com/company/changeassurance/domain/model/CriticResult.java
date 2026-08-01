package com.company.changeassurance.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Result of the controlled AI critic pass, validated deterministically by the application.
 */
public record CriticResult(
        String availabilityStatus,
        boolean accepted,
        List<String> unsupportedClaims,
        List<String> missingDeterministicFindings,
        List<String> contradictions,
        List<String> weakEvidenceReferences,
        boolean attemptedRecommendationOverride,
        String summary
) {

    public static final String UNAVAILABLE = "UNAVAILABLE";

    public CriticResult {
        Objects.requireNonNull(availabilityStatus, "availabilityStatus must not be null");
        unsupportedClaims = List.copyOf(Objects.requireNonNullElse(unsupportedClaims, List.of()));
        missingDeterministicFindings = List.copyOf(
                Objects.requireNonNullElse(missingDeterministicFindings, List.of()));
        contradictions = List.copyOf(Objects.requireNonNullElse(contradictions, List.of()));
        weakEvidenceReferences = List.copyOf(Objects.requireNonNullElse(weakEvidenceReferences, List.of()));
    }

    public static CriticResult unavailable() {
        return new CriticResult(
                UNAVAILABLE,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                "Critic review unavailable because the model gateway is disabled or unreachable."
        );
    }
}
