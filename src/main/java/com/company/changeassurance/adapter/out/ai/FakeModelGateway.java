package com.company.changeassurance.adapter.out.ai;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.domain.model.AiTaskType;

/**
 * Test and local-development model gateway with registerable stub responses.
 * Does not call any external LLM provider.
 */
public final class FakeModelGateway implements ModelGateway {

    private final Map<Class<?>, Supplier<?>> responsesByType = new ConcurrentHashMap<>();
    private final Map<AiTaskType, Supplier<?>> responsesByTask = new ConcurrentHashMap<>();

    public <T> void register(Class<T> responseType, T response) {
        Objects.requireNonNull(responseType, "responseType must not be null");
        Objects.requireNonNull(response, "response must not be null");
        responsesByType.put(responseType, () -> response);
    }

    public <T> void register(Class<T> responseType, Supplier<T> supplier) {
        Objects.requireNonNull(responseType, "responseType must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        responsesByType.put(responseType, supplier);
    }

    public <T> void register(AiTaskType taskType, Supplier<T> supplier) {
        Objects.requireNonNull(taskType, "taskType must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        responsesByTask.put(taskType, supplier);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String defaultModel() {
        return "fake";
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(AiTaskType taskType, AiRequest request, Class<T> responseType) {
        Objects.requireNonNull(taskType, "taskType must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(responseType, "responseType must not be null");

        Supplier<?> byTask = responsesByTask.get(taskType);
        if (byTask != null) {
            Object value = byTask.get();
            return responseType.cast(value);
        }

        Supplier<?> byType = responsesByType.get(responseType);
        if (byType != null) {
            Object value = byType.get();
            return responseType.cast(value);
        }

        throw new IllegalStateException(
                "No fake response registered for task " + taskType + " or type " + responseType.getName()
        );
    }
}
