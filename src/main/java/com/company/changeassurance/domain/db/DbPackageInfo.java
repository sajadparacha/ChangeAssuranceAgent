package com.company.changeassurance.domain.db;

import java.util.Objects;

/**
 * Catalog identity for an Oracle PL/SQL package.
 */
public record DbPackageInfo(
        String owner,
        String packageName,
        boolean specPresent,
        boolean bodyPresent,
        String status
) {
    public DbPackageInfo {
        Objects.requireNonNull(packageName, "packageName must not be null");
        if (packageName.isBlank()) {
            throw new IllegalArgumentException("packageName must not be blank");
        }
        packageName = packageName.trim().toUpperCase();
        owner = owner == null || owner.isBlank() ? null : owner.trim().toUpperCase();
        status = status == null || status.isBlank() ? "UNKNOWN" : status.trim().toUpperCase();
    }

    public String qualifiedName() {
        return owner == null ? packageName : owner + "." + packageName;
    }
}
