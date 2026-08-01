package com.company.changeassurance.domain.model;

import java.util.Objects;

public record PlanId(String value) {

    public PlanId {
        Objects.requireNonNull(value, "planId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("planId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
