package com.company.changeassurance.adapter.out.ai;

import java.util.Objects;

import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.domain.exception.AiUnavailableException;
import com.company.changeassurance.domain.model.AiTaskType;

/**
 * Model gateway used when AI is explicitly disabled.
 * Deterministic review continues; AI sections surface as UNAVAILABLE in later phases.
 */
public final class DisabledModelGateway implements ModelGateway {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public <T> T execute(AiTaskType taskType, AiRequest request, Class<T> responseType) {
        Objects.requireNonNull(taskType, "taskType must not be null");
        throw new AiUnavailableException(
                "AI is disabled; task " + taskType + " cannot be executed. Deterministic checks may still proceed."
        );
    }
}
