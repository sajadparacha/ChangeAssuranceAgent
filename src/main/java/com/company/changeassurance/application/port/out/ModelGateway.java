package com.company.changeassurance.application.port.out;

import java.util.Locale;
import java.util.Set;

import com.company.changeassurance.domain.model.AiTaskType;

/**
 * Port for controlled AI reasoning tasks. Implementations must be interchangeable (LSP).
 *
 * <p>Providers: Fake (tests), Disabled (AI off), Spring AI / OpenAI-compatible (ChatGPT, Ollama, LM Studio).
 */
public interface ModelGateway {

    String PROVIDER_OPENAI = "openai";
    String PROVIDER_OLLAMA = "ollama";

    static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return PROVIDER_OPENAI;
        }
        String trimmed = provider.trim().toLowerCase(Locale.ROOT);
        if ("chatgpt".equals(trimmed) || "spring-ai".equals(trimmed) || "cloud".equals(trimmed)) {
            return PROVIDER_OPENAI;
        }
        if ("local".equals(trimmed) || "ollama".equals(trimmed)) {
            return PROVIDER_OLLAMA;
        }
        return trimmed;
    }

    /**
     * @return {@code true} when the gateway can serve AI tasks
     */
    boolean isAvailable();

    /**
     * @return configured default model id (never blank; may be {@code none} or {@code fake})
     */
    String defaultModel();

    /**
     * Default provider id when the client does not specify one.
     */
    default String defaultProvider() {
        return PROVIDER_OPENAI;
    }

    /**
     * @return known provider ids for multi-provider gateways; empty for single-endpoint gateways
     */
    default Set<String> providerIds() {
        return Set.of();
    }

    /**
     * @return nested gateway for a provider, or {@code null} when unknown
     */
    default ModelGateway provider(String providerId) {
        return null;
    }

    default boolean hasProvider(String providerId) {
        return providerId != null && providerIds().contains(normalizeProvider(providerId));
    }

    /**
     * Executes a typed AI task and returns a validated structured response.
     *
     * @param taskType     controlled task enum
     * @param request      framework-free request envelope
     * @param responseType expected response class
     * @param <T>          response type
     * @return structured response instance
     */
    <T> T execute(AiTaskType taskType, AiRequest request, Class<T> responseType);

    /**
     * Untrusted content and task metadata for a model call. System instructions are owned by adapters.
     */
    record AiRequest(
            String reviewId,
            String promptVersion,
            String untrustedContent,
            int maxInputChars,
            int maxOutputChars,
            String modelOverride,
            String providerOverride
    ) {
        public AiRequest(
                String reviewId,
                String promptVersion,
                String untrustedContent,
                int maxInputChars,
                int maxOutputChars
        ) {
            this(reviewId, promptVersion, untrustedContent, maxInputChars, maxOutputChars, null, null);
        }

        public AiRequest(
                String reviewId,
                String promptVersion,
                String untrustedContent,
                int maxInputChars,
                int maxOutputChars,
                String modelOverride
        ) {
            this(reviewId, promptVersion, untrustedContent, maxInputChars, maxOutputChars, modelOverride, null);
        }

        public AiRequest {
            if (reviewId == null || reviewId.isBlank()) {
                throw new IllegalArgumentException("reviewId must not be blank");
            }
            if (promptVersion == null || promptVersion.isBlank()) {
                throw new IllegalArgumentException("promptVersion must not be blank");
            }
            if (maxInputChars < 1) {
                throw new IllegalArgumentException("maxInputChars must be >= 1");
            }
            if (maxOutputChars < 1) {
                throw new IllegalArgumentException("maxOutputChars must be >= 1");
            }
            if (modelOverride != null && modelOverride.isBlank()) {
                modelOverride = null;
            }
            if (providerOverride != null && providerOverride.isBlank()) {
                providerOverride = null;
            }
        }
    }
}
