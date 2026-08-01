package com.company.changeassurance.domain.model;

import java.util.Objects;

public record PlanStepId(String value) {

    public PlanStepId {
        Objects.requireNonNull(value, "planStepId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("planStepId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
