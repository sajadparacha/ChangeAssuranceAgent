package com.company.changeassurance.domain.model;

import java.util.Objects;

/**
 * Database or application object touched by the change package.
 */
public record AffectedObject(
        String objectName,
        String objectType,
        String schemaName,
        String changeKind,
        EvidenceId evidenceId
) {

    public AffectedObject {
        Objects.requireNonNull(objectName, "objectName must not be null");
        if (objectName.isBlank()) {
            throw new IllegalArgumentException("objectName must not be blank");
        }
        Objects.requireNonNull(objectType, "objectType must not be null");
    }
}
