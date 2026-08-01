package com.company.changeassurance.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * AI-assisted classification of a submitted change. Enum values and evidence refs must be validated.
 */
public record ChangeClassification(
        ChangeType primaryChangeType,
        List<ChangeType> secondaryChangeTypes,
        Complexity complexity,
        double confidence,
        List<ReviewCapability> requiredReviewCapabilities,
        List<EvidenceId> evidenceIds
) {

    public ChangeClassification {
        Objects.requireNonNull(primaryChangeType, "primaryChangeType must not be null");
        secondaryChangeTypes = List.copyOf(
                Objects.requireNonNull(secondaryChangeTypes, "secondaryChangeTypes must not be null"));
        Objects.requireNonNull(complexity, "complexity must not be null");
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0 inclusive");
        }
        requiredReviewCapabilities = List.copyOf(Objects.requireNonNull(
                requiredReviewCapabilities, "requiredReviewCapabilities must not be null"));
        evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds must not be null"));
    }
}
