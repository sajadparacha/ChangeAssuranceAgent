package com.company.changeassurance.adapter.out.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.company.changeassurance.application.port.out.ModelCatalogPort;
import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.configuration.ChangeAssuranceProperties;

/**
 * Lists models from an OpenAI-compatible {@code GET /models} endpoint (Ollama, LM Studio, OpenAI).
 */
@Component
public class OpenAiCompatibleModelCatalogAdapter implements ModelCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleModelCatalogAdapter.class);

    private final ChangeAssuranceProperties properties;
    private final ModelGateway modelGateway;
    private final Environment environment;
    private final RestClient.Builder restClientBuilder;

    public OpenAiCompatibleModelCatalogAdapter(
            ChangeAssuranceProperties properties,
            ModelGateway modelGateway,
            Environment environment,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.modelGateway = modelGateway;
        this.environment = environment;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public List<CatalogModel> listModels() {
        return listModelsAt(resolveBaseUrl());
    }

    @Override
    public List<CatalogModel> listModelsAt(String baseUrl) {
        String normalized = SpringAiModelGateway.normalizeBaseUrl(baseUrl);
        if (normalized == null) {
            return List.of();
        }
        try {
            RestClient client = restClientBuilder
                    .baseUrl(normalized)
                    .build();
            Map<String, Object> body = client.get()
                    .uri("/models")
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + resolveApiKey(normalized))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return parseModels(body);
        } catch (RuntimeException ex) {
            log.debug("Model catalog discovery failed for baseUrl={}: {}", normalized, ex.getMessage());
            return List.of();
        }
    }

    private String resolveBaseUrl() {
        String configured = SpringAiModelGateway.normalizeBaseUrl(properties.ai().baseUrl());
        if (configured != null) {
            return configured;
        }
        if (properties.ai().isLocalMode()) {
            return ChangeAssuranceProperties.DEFAULT_OLLAMA_BASE_URL;
        }
        if (modelGateway.isAvailable() && properties.ai().isOpenAiCompatibleMode()) {
            return "https://api.openai.com/v1";
        }
        return null;
    }

    private String resolveApiKey(String baseUrl) {
        boolean local = baseUrl != null && (baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1"));
        String apiKey = firstNonBlank(
                environment.getProperty("spring.ai.openai.api-key"),
                environment.getProperty("OPENAI_API_KEY")
        );
        if (apiKey == null || apiKey.isBlank() || local) {
            if (local) {
                return ChangeAssuranceProperties.LOCAL_PLACEHOLDER_API_KEY;
            }
            if (apiKey == null || apiKey.isBlank()) {
                return ChangeAssuranceProperties.LOCAL_PLACEHOLDER_API_KEY;
            }
        }
        return apiKey;
    }

    @SuppressWarnings("unchecked")
    private static List<CatalogModel> parseModels(Map<String, Object> body) {
        if (body == null) {
            return List.of();
        }
        Object data = body.get("data");
        if (!(data instanceof List<?> rows)) {
            return List.of();
        }
        List<CatalogModel> models = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            Object id = map.get("id");
            if (id == null || id.toString().isBlank()) {
                continue;
            }
            Object ownedBy = map.get("owned_by");
            models.add(new CatalogModel(
                    id.toString().trim(),
                    ownedBy == null ? "unknown" : ownedBy.toString()
            ));
        }
        return List.copyOf(models);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
