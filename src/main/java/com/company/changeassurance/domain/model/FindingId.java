package com.company.changeassurance.domain.model;

import java.util.Objects;

public record FindingId(String value) {

    public FindingId {
        Objects.requireNonNull(value, "findingId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("findingId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
