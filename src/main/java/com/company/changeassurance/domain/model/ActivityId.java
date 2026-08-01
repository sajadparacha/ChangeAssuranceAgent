package com.company.changeassurance.domain.model;

import java.util.Objects;

public record ActivityId(String value) {

    public ActivityId {
        Objects.requireNonNull(value, "activityId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("activityId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
