package com.company.changeassurance.adapter.out.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.domain.exception.AiUnavailableException;
import com.company.changeassurance.domain.model.AiTaskType;

/**
 * Routes AI calls to a provider-specific {@link ModelGateway} based on
 * {@link AiRequest#providerOverride()} (or the configured default provider).
 */
public final class RoutingModelGateway implements ModelGateway {

    private final String defaultProvider;
    private final Map<String, ModelGateway> providers;

    public RoutingModelGateway(String defaultProvider, Map<String, ModelGateway> providers) {
        Objects.requireNonNull(defaultProvider, "defaultProvider");
        Objects.requireNonNull(providers, "providers");
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("providers must not be empty");
        }
        String normalizedDefault = ModelGateway.normalizeProvider(defaultProvider);
        Map<String, ModelGateway> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ModelGateway> entry : providers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            copy.put(ModelGateway.normalizeProvider(entry.getKey()), entry.getValue());
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("providers must not be empty");
        }
        if (!copy.containsKey(normalizedDefault)) {
            normalizedDefault = copy.keySet().iterator().next();
        }
        this.defaultProvider = normalizedDefault;
        this.providers = Map.copyOf(copy);
    }

    @Override
    public boolean isAvailable() {
        return providers.values().stream().anyMatch(ModelGateway::isAvailable);
    }

    @Override
    public String defaultModel() {
        ModelGateway gateway = providers.get(defaultProvider);
        if (gateway != null && gateway.isAvailable()) {
            return gateway.defaultModel();
        }
        return providers.values().stream()
                .filter(ModelGateway::isAvailable)
                .map(ModelGateway::defaultModel)
                .findFirst()
                .orElseGet(() -> providers.values().iterator().next().defaultModel());
    }

    @Override
    public String defaultProvider() {
        return defaultProvider;
    }

    @Override
    public Set<String> providerIds() {
        return providers.keySet();
    }

    @Override
    public ModelGateway provider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return providers.get(defaultProvider);
        }
        return providers.get(ModelGateway.normalizeProvider(providerId));
    }

    @Override
    public boolean hasProvider(String providerId) {
        return providerId != null && providers.containsKey(ModelGateway.normalizeProvider(providerId));
    }

    @Override
    public <T> T execute(AiTaskType taskType, AiRequest request, Class<T> responseType) {
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(responseType, "responseType");

        String providerId = resolveProvider(request);
        ModelGateway gateway = providers.get(providerId);
        if (gateway == null) {
            throw new AiUnavailableException("Unknown AI provider '" + providerId + "'");
        }
        if (!gateway.isAvailable()) {
            throw new AiUnavailableException("AI provider '" + providerId + "' is not available");
        }
        return gateway.execute(taskType, request, responseType);
    }

    private String resolveProvider(AiRequest request) {
        if (request.providerOverride() != null && !request.providerOverride().isBlank()) {
            return ModelGateway.normalizeProvider(request.providerOverride());
        }
        return defaultProvider;
    }
}
