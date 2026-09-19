package com.company.changeassurance.adapter.out.ai;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;

import com.company.changeassurance.adapter.out.ai.AiStructuredResponses.ClassificationResponse;
import com.company.changeassurance.adapter.out.ai.AiStructuredResponses.GapQuestionsResponse;
import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.domain.exception.AiUnavailableException;
import com.company.changeassurance.domain.model.AiTaskType;
import com.company.changeassurance.domain.model.ChangeClassification;

/**
 * OpenAI-compatible ModelGateway via Spring AI.
 * Works with ChatGPT, Ollama, LM Studio, and other OpenAI-compatible local servers.
 * Fails closed when no API key / client is configured.
 */
public final class SpringAiModelGateway implements ModelGateway {

    private static final Logger log = LoggerFactory.getLogger(SpringAiModelGateway.class);

    private final boolean enabled;
    private final String modelIdentifier;
    private final String baseUrl;
    private final ChatClient chatClient;

    public SpringAiModelGateway(boolean enabled, String modelIdentifier) {
        this(enabled, modelIdentifier, null, null);
    }

    public SpringAiModelGateway(boolean enabled, String modelIdentifier, ChatClient chatClient) {
        this(enabled, modelIdentifier, null, chatClient);
    }

    public SpringAiModelGateway(
            boolean enabled,
            String modelIdentifier,
            String baseUrl,
            ChatClient chatClient) {
        this.enabled = enabled && chatClient != null;
        this.modelIdentifier = modelIdentifier == null || modelIdentifier.isBlank() ? "gpt-4o-mini" : modelIdentifier;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.chatClient = chatClient;
    }

    /**
     * Builds a gateway against the default OpenAI API host.
     */
    public static SpringAiModelGateway create(String apiKey, String modelIdentifier) {
        return create(apiKey, modelIdentifier, null);
    }

    /**
     * Builds a live OpenAI-compatible gateway.
     *
     * @param baseUrl optional OpenAI-compatible base URL (e.g. {@code http://localhost:11434/v1} for Ollama)
     */
    public static SpringAiModelGateway create(String apiKey, String modelIdentifier, String baseUrl) {
        Objects.requireNonNull(apiKey, "apiKey");
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        String model = modelIdentifier == null || modelIdentifier.isBlank() || "none".equalsIgnoreCase(modelIdentifier)
                ? "gpt-4o-mini"
                : modelIdentifier.trim();
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);

        OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(apiKey.trim());
        if (normalizedBaseUrl != null) {
            apiBuilder.baseUrl(normalizedBaseUrl);
        }
        OpenAiApi openAiApi = apiBuilder.build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(0.2d)
                        .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                        .build())
                .build();
        return new SpringAiModelGateway(true, model, normalizedBaseUrl, ChatClient.create(chatModel));
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    @Override
    public String defaultModel() {
        return modelIdentifier;
    }

    @Override
    public <T> T execute(AiTaskType taskType, AiRequest request, Class<T> responseType) {
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(responseType, "responseType");
        if (!enabled || chatClient == null) {
            throw new AiUnavailableException(
                    "OpenAI-compatible gateway is not configured (model=" + modelIdentifier + ")");
        }

        String effectiveModel = resolveModel(request);
        String userContent = truncate(request.untrustedContent(), request.maxInputChars());
        int maxTokens = Math.max(256, Math.min(request.maxOutputChars(), 4000));

        try {
            if (responseType == ChangeClassification.class) {
                ClassificationResponse parsed = chatClient.prompt()
                        .system(OpenAiTaskPrompts.systemPrompt(taskType))
                        .user(userContent)
                        .options(chatOptions(effectiveModel, maxTokens))
                        .call()
                        .entity(ClassificationResponse.class);
                return responseType.cast(parsed.toDomain());
            }
            if (responseType == List.class) {
                GapQuestionsResponse parsed = chatClient.prompt()
                        .system(OpenAiTaskPrompts.systemPrompt(taskType))
                        .user(userContent)
                        .options(chatOptions(effectiveModel, maxTokens))
                        .call()
                        .entity(GapQuestionsResponse.class);
                return responseType.cast(parsed.safeQuestions());
            }

            T parsed = chatClient.prompt()
                    .system(OpenAiTaskPrompts.systemPrompt(taskType))
                    .user(userContent)
                    .options(chatOptions(effectiveModel, maxTokens))
                    .call()
                    .entity(responseType);
            return parsed;
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Model call failed for task {} review {} (model={}, baseUrl={}): {}",
                    taskType, request.reviewId(), effectiveModel, baseUrl == null ? "default" : baseUrl, ex.getMessage());
            throw new AiUnavailableException(
                    "Model call failed for task " + taskType + ": " + safeMessage(ex), ex);
        }
    }

    public String modelIdentifier() {
        return modelIdentifier;
    }

    private String resolveModel(AiRequest request) {
        if (request.modelOverride() != null && !request.modelOverride().isBlank()) {
            return request.modelOverride().trim();
        }
        return modelIdentifier;
    }

    private static OpenAiChatOptions chatOptions(String model, int maxTokens) {
        return OpenAiChatOptions.builder()
                .model(model)
                .maxTokens(maxTokens)
                .temperature(0.2d)
                .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                .build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String truncate(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars);
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        // Avoid leaking key material if a provider echoes request headers.
        return message.replaceAll("(?i)sk-[a-zA-Z0-9\\-_]+", "[REDACTED]");
    }
}
