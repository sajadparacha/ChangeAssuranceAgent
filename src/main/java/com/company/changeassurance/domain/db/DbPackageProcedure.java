package com.company.changeassurance.domain.db;

import java.util.Objects;

/**
 * Procedure or function declared in a PL/SQL package.
 */
public record DbPackageProcedure(String name, String procedureType) {
    public DbPackageProcedure {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        name = name.trim().toUpperCase();
        procedureType = procedureType == null || procedureType.isBlank()
                ? "PROCEDURE"
                : procedureType.trim().toUpperCase();
    }
}
