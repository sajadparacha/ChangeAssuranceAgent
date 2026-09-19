package com.company.changeassurance.domain.db;

import java.util.Objects;

/**
 * A line hit from read-only Oracle source dictionary search ({@code ALL_SOURCE}).
 */
public record DbSourceReference(
        String owner,
        String objectName,
        String objectType,
        int line,
        String excerpt
) {
    public DbSourceReference {
        Objects.requireNonNull(objectName, "objectName must not be null");
        if (objectName.isBlank()) {
            throw new IllegalArgumentException("objectName must not be blank");
        }
        objectName = objectName.trim().toUpperCase();
        owner = owner == null || owner.isBlank() ? null : owner.trim().toUpperCase();
        objectType = objectType == null || objectType.isBlank() ? "OBJECT" : objectType.trim().toUpperCase();
        if (line < 0) {
            line = 0;
        }
        excerpt = excerpt == null ? "" : excerpt;
        if (excerpt.length() > 400) {
            excerpt = excerpt.substring(0, 400) + "...";
        }
    }

    public String qualifiedName() {
        return owner == null ? objectName : owner + "." + objectName;
    }
}
