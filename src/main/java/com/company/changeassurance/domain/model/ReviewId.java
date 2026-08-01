package com.company.changeassurance.domain.model;

import java.util.Objects;

/**
 * Typed review identifier. Format is assigned by the application layer (e.g. REV-2026-00001).
 */
public record ReviewId(String value) {

    public ReviewId {
        Objects.requireNonNull(value, "reviewId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("reviewId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
