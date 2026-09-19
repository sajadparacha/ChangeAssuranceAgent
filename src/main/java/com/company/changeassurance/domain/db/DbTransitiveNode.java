package com.company.changeassurance.domain.db;

import java.util.Objects;

/**
 * A dependent discovered beyond the direct (1-hop) blast radius.
 */
public record DbTransitiveNode(DbObjectRef object, int depth) {
    public DbTransitiveNode {
        Objects.requireNonNull(object, "object must not be null");
        if (depth < 2) {
            throw new IllegalArgumentException("transitive depth must be >= 2");
        }
    }
}
