package com.company.changeassurance.domain.model;

import java.util.Objects;

public record GapId(String value) {

    public GapId {
        Objects.requireNonNull(value, "gapId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("gapId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
