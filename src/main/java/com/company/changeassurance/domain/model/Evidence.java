package com.company.changeassurance.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Evidence(
        EvidenceId evidenceId,
        EvidenceType evidenceType,
        EvidenceSource source,
        String sourceReference,
        String description,
        String extractedContent,
        Integer lineNumber,
        String contentHash,
        Instant createdAt
) {

    public Evidence {
        Objects.requireNonNull(evidenceId, "evidenceId must not be null");
        Objects.requireNonNull(evidenceType, "evidenceType must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
