package com.company.changeassurance.domain.db;

import java.util.Objects;

/**
 * Database catalog object reference (schema-qualified when owner is known).
 */
public record DbObjectRef(
        String owner,
        String objectName,
        String objectType,
        String status
) {
    public DbObjectRef {
        Objects.requireNonNull(objectName, "objectName must not be null");
        if (objectName.isBlank()) {
            throw new IllegalArgumentException("objectName must not be blank");
        }
        objectType = objectType == null || objectType.isBlank() ? "OBJECT" : objectType.trim().toUpperCase();
        owner = owner == null || owner.isBlank() ? null : owner.trim().toUpperCase();
        status = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        objectName = objectName.trim().toUpperCase();
    }

    public String qualifiedName() {
        return owner == null ? objectName : owner + "." + objectName;
    }
}
