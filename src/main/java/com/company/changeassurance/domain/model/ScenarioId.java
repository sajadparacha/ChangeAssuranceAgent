package com.company.changeassurance.domain.model;

import java.util.Objects;

public record ScenarioId(String value) {

    public ScenarioId {
        Objects.requireNonNull(value, "scenarioId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("scenarioId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
