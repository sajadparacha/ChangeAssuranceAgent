package com.company.changeassurance.domain.model;

import java.util.Objects;

public record EvidenceId(String value) {

    public EvidenceId {
        Objects.requireNonNull(value, "evidenceId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("evidenceId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
