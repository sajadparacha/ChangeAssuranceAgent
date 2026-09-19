package com.company.changeassurance.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.company.changeassurance.application.port.in.GetAiConfigUseCase;
import com.company.changeassurance.application.port.out.ModelCatalogPort;
import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.configuration.ChangeAssuranceProperties;
import com.company.changeassurance.domain.exception.DomainValidationException;

/**
 * Resolves selectable AI providers/models and validates per-review overrides.
 */
@Service
public class AiConfigService implements GetAiConfigUseCase {

    private static final Pattern MODEL_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$");
    private static final List<String> CLOUD_SUGGESTIONS = List.of(
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-4.1-mini",
            "gpt-4.1"
    );

    private final ChangeAssuranceProperties properties;
    private final ModelGateway modelGateway;
    private final ModelCatalogPort modelCatalogPort;

    public AiConfigService(
            ChangeAssuranceProperties properties,
            ModelGateway modelGateway,
            ModelCatalogPort modelCatalogPort
    ) {
        this.properties = properties;
        this.modelGateway = modelGateway;
        this.modelCatalogPort = modelCatalogPort;
    }

    @Override
    public AiConfigResult getConfig() {
        List<ProviderOption> providers = buildProviders();
        String defaultProvider = resolveDefaultProviderId(providers);
        ProviderOption defaultProviderOption = providers.stream()
                .filter(p -> p.id().equals(defaultProvider))
                .findFirst()
                .orElse(null);

        String defaultModel = defaultProviderOption != null
                ? defaultProviderOption.defaultModel()
                : modelGateway.defaultModel();
        List<ModelOption> flatModels = defaultProviderOption != null
                ? defaultProviderOption.models()
                : List.of();
        boolean allowsCustom = defaultProviderOption != null && defaultProviderOption.allowsCustomModel();
        String baseUrl = defaultProviderOption != null ? defaultProviderOption.baseUrl() : "";

        return new AiConfigResult(
                modelGateway.isAvailable() || providers.stream().anyMatch(ProviderOption::available),
                properties.ai().mode(),
                defaultProvider,
                defaultModel,
                baseUrl == null ? "" : baseUrl,
                allowsCustom,
                flatModels,
                providers
        );
    }

    /**
     * Validates optional provider + model and returns the effective selection for the review.
     */
    public ResolvedAiSelection resolveSelection(String requestedProvider, String requestedModel) {
        List<ProviderOption> providers = buildProviders();
        String provider = normalizeRequestedProvider(requestedProvider, providers);
        String model = resolveSelectedModel(requestedModel, provider, providers);
        return new ResolvedAiSelection(provider, model);
    }

    /**
     * @deprecated prefer {@link #resolveSelection(String, String)}
     */
    public String resolveSelectedModel(String requestedModel) {
        return resolveSelection(null, requestedModel).model();
    }

    private String resolveSelectedModel(
            String requestedModel,
            String provider,
            List<ProviderOption> providers
    ) {
        String defaultModel = providers.stream()
                .filter(p -> p.id().equals(provider))
                .map(ProviderOption::defaultModel)
                .findFirst()
                .orElseGet(modelGateway::defaultModel);

        if (requestedModel == null || requestedModel.isBlank()) {
            return defaultModel;
        }
        String trimmed = requestedModel.trim();
        if (!MODEL_ID_PATTERN.matcher(trimmed).matches()) {
            throw new DomainValidationException(
                    "aiModel must be 1-128 characters and contain only letters, digits, and . _ : / -"
            );
        }
        List<String> allowed = properties.ai().allowedModelList();
        if (!allowed.isEmpty() && allowed.stream().noneMatch(m -> m.equalsIgnoreCase(trimmed))) {
            throw new DomainValidationException(
                    "aiModel '" + trimmed + "' is not in the allowed model list"
            );
        }
        return trimmed;
    }

    private String normalizeRequestedProvider(String requestedProvider, List<ProviderOption> providers) {
        if (providers.isEmpty()) {
            return requestedProvider == null || requestedProvider.isBlank()
                    ? ""
                    : ModelGateway.normalizeProvider(requestedProvider);
        }
        String defaultProvider = resolveDefaultProviderId(providers);
        if (requestedProvider == null || requestedProvider.isBlank()) {
            return defaultProvider;
        }
        String normalized = ModelGateway.normalizeProvider(requestedProvider);
        boolean known = providers.stream().anyMatch(p -> p.id().equals(normalized));
        if (!known) {
            throw new DomainValidationException(
                    "aiProvider '" + requestedProvider + "' is not available. Choose: "
                            + providers.stream().map(ProviderOption::id).toList()
            );
        }
        return normalized;
    }

    private List<ProviderOption> buildProviders() {
        if (!properties.ai().isOpenAiCompatibleMode()) {
            return List.of();
        }

        List<ProviderOption> providers = new ArrayList<>();
        List<String> allowed = properties.ai().allowedModelList();
        boolean allowCustomGlobal = allowed.isEmpty();

        if (!modelGateway.providerIds().isEmpty()) {
            if (modelGateway.hasProvider(ModelGateway.PROVIDER_OPENAI)) {
                ModelGateway openai = modelGateway.provider(ModelGateway.PROVIDER_OPENAI);
                providers.add(buildOpenAiProvider(openai, allowCustomGlobal, allowed));
            }
            if (modelGateway.hasProvider(ModelGateway.PROVIDER_OLLAMA) && properties.ai().ollamaEnabled()) {
                ModelGateway ollama = modelGateway.provider(ModelGateway.PROVIDER_OLLAMA);
                providers.add(buildOllamaProvider(ollama, allowCustomGlobal, allowed));
            }
            return List.copyOf(providers);
        }

        // Non-routing gateway (single endpoint)
        if (properties.ai().isLocalMode()) {
            providers.add(buildOllamaProvider(modelGateway, allowCustomGlobal, allowed));
        } else if (modelGateway.isAvailable()) {
            providers.add(buildOpenAiProvider(modelGateway, allowCustomGlobal, allowed));
            if (properties.ai().ollamaEnabled()) {
                providers.add(buildOllamaProvider(null, allowCustomGlobal, allowed));
            }
        }
        return List.copyOf(providers);
    }

    private ProviderOption buildOpenAiProvider(
            ModelGateway gateway,
            boolean allowCustomGlobal,
            List<String> allowed
    ) {
        String defaultModel = gateway != null && gateway.defaultModel() != null
                ? gateway.defaultModel()
                : ChangeAssuranceProperties.DEFAULT_CLOUD_MODEL;
        if ("fake".equalsIgnoreCase(defaultModel) || "none".equalsIgnoreCase(defaultModel)) {
            defaultModel = ChangeAssuranceProperties.DEFAULT_CLOUD_MODEL;
        }
        Map<String, ModelOption> options = new LinkedHashMap<>();
        addOption(options, defaultModel, true);
        for (String allowedModel : allowed) {
            addOption(options, allowedModel, allowedModel.equals(defaultModel));
        }
        if (allowCustomGlobal) {
            for (ModelCatalogPort.CatalogModel catalogModel : modelCatalogPort.listModelsAt("https://api.openai.com/v1")) {
                addOption(options, catalogModel.id(), catalogModel.id().equals(defaultModel));
            }
            for (String suggestion : CLOUD_SUGGESTIONS) {
                addOption(options, suggestion, suggestion.equals(defaultModel));
            }
        }
        boolean available = gateway != null && gateway.isAvailable();
        boolean allowsCustom = allowCustomGlobal && available;
        return new ProviderOption(
                ModelGateway.PROVIDER_OPENAI,
                "ChatGPT / OpenAI",
                available,
                "",
                defaultModel,
                allowsCustom,
                List.copyOf(options.values())
        );
    }

    private ProviderOption buildOllamaProvider(
            ModelGateway gateway,
            boolean allowCustomGlobal,
            List<String> allowed
    ) {
        String ollamaUrl = properties.ai().resolvedOllamaBaseUrl();
        String configuredDefault = gateway != null
                && gateway.defaultModel() != null
                && !"fake".equalsIgnoreCase(gateway.defaultModel())
                && !"none".equalsIgnoreCase(gateway.defaultModel())
                ? gateway.defaultModel()
                : ChangeAssuranceProperties.DEFAULT_LOCAL_MODEL;

        Map<String, ModelOption> options = new LinkedHashMap<>();
        addOption(options, configuredDefault, true);
        for (String allowedModel : allowed) {
            addOption(options, allowedModel, allowedModel.equals(configuredDefault));
        }
        List<ModelCatalogPort.CatalogModel> discovered = modelCatalogPort.listModelsAt(ollamaUrl);
        for (ModelCatalogPort.CatalogModel catalogModel : discovered) {
            addOption(options, catalogModel.id(), catalogModel.id().equals(configuredDefault));
        }
        // In cloud mode, if the generic Ollama default is not installed, prefer an installed model.
        String defaultModel = configuredDefault;
        if (!properties.ai().isLocalMode()
                && !discovered.isEmpty()
                && ChangeAssuranceProperties.DEFAULT_LOCAL_MODEL.equalsIgnoreCase(configuredDefault)
                && discovered.stream().noneMatch(m -> m.id().equalsIgnoreCase(configuredDefault))) {
            String first = discovered.get(0).id();
            Map<String, ModelOption> rebuilt = new LinkedHashMap<>();
            addOption(rebuilt, first, true);
            for (ModelCatalogPort.CatalogModel catalogModel : discovered) {
                addOption(rebuilt, catalogModel.id(), catalogModel.id().equals(first));
            }
            addOption(rebuilt, configuredDefault, false);
            for (String allowedModel : allowed) {
                addOption(rebuilt, allowedModel, allowedModel.equals(first));
            }
            options.clear();
            options.putAll(rebuilt);
            defaultModel = first;
        }

        boolean reachable = !discovered.isEmpty() || (gateway != null && gateway.isAvailable());
        boolean allowsCustom = allowCustomGlobal && reachable;
        return new ProviderOption(
                ModelGateway.PROVIDER_OLLAMA,
                "Ollama (local)",
                reachable,
                ollamaUrl == null ? "" : ollamaUrl,
                defaultModel,
                allowsCustom,
                List.copyOf(options.values())
        );
    }

    private String resolveDefaultProviderId(List<ProviderOption> providers) {
        if (!modelGateway.providerIds().isEmpty()) {
            return modelGateway.defaultProvider();
        }
        if (properties.ai().isLocalMode()) {
            return ModelGateway.PROVIDER_OLLAMA;
        }
        if (!providers.isEmpty()) {
            return providers.get(0).id();
        }
        return ModelGateway.PROVIDER_OPENAI;
    }

    private static void addOption(Map<String, ModelOption> options, String id, boolean isDefault) {
        if (id == null || id.isBlank() || "none".equalsIgnoreCase(id) || "fake".equalsIgnoreCase(id)) {
            return;
        }
        String key = id.toLowerCase(Locale.ROOT);
        ModelOption existing = options.get(key);
        if (existing == null) {
            options.put(key, new ModelOption(id, id, isDefault));
        } else if (isDefault && !existing.isDefault()) {
            options.put(key, new ModelOption(existing.id(), existing.label(), true));
        }
    }

    public record ResolvedAiSelection(String provider, String model) {
    }
}
