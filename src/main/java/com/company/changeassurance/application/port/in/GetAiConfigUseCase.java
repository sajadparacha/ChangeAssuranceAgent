package com.company.changeassurance.application.port.in;

import java.util.List;

/**
 * Read model / AI runtime configuration for clients that need to select a provider and model.
 */
public interface GetAiConfigUseCase {

    AiConfigResult getConfig();

    record AiConfigResult(
            boolean available,
            String mode,
            String defaultProvider,
            String defaultModel,
            String baseUrl,
            boolean allowsCustomModel,
            List<ModelOption> models,
            List<ProviderOption> providers
    ) {
    }

    record ProviderOption(
            String id,
            String label,
            boolean available,
            String baseUrl,
            String defaultModel,
            boolean allowsCustomModel,
            List<ModelOption> models
    ) {
    }

    record ModelOption(String id, String label, boolean isDefault) {
    }
}
