package com.company.changeassurance.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.company.changeassurance.application.port.out.ModelCatalogPort;
import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.configuration.ChangeAssuranceProperties;
import com.company.changeassurance.domain.exception.AiUnavailableException;
import com.company.changeassurance.domain.model.AiTaskType;

class AiConfigServiceTest {

    @Test
    void resolvesDefaultWhenBlank() {
        AiConfigService service = service("spring-ai", "gpt-4o-mini", null, List.of(), true);
        assertThat(service.resolveSelectedModel(null)).isEqualTo("gpt-4o-mini");
        assertThat(service.resolveSelectedModel("  ")).isEqualTo("gpt-4o-mini");
    }

    @Test
    void acceptsExplicitModelWhenAllowlistEmpty() {
        AiConfigService service = service("ollama", "qwen3:14b", null, List.of(), true);
        assertThat(service.resolveSelectedModel("qwen3:8b")).isEqualTo("qwen3:8b");
    }

    @Test
    void rejectsModelOutsideAllowlist() {
        AiConfigService service = service("ollama", "qwen3:14b", "qwen3:14b,qwen3:8b", List.of(), true);
        assertThatThrownBy(() -> service.resolveSelectedModel("llama3.1:8b"))
                .isInstanceOf(com.company.changeassurance.domain.exception.DomainValidationException.class)
                .hasMessageContaining("allowed model list");
    }

    @Test
    void rejectsInvalidModelId() {
        AiConfigService service = service("ollama", "qwen3:14b", null, List.of(), true);
        assertThatThrownBy(() -> service.resolveSelectedModel("bad model!"))
                .isInstanceOf(com.company.changeassurance.domain.exception.DomainValidationException.class)
                .hasMessageContaining("aiModel must be");
    }

    @Test
    void configIncludesDiscoveredAndDefaultModels() {
        AiConfigService service = service(
                "ollama",
                "qwen3:14b",
                null,
                List.of(new ModelCatalogPort.CatalogModel("qwen3:8b", "ollama")),
                true
        );
        var config = service.getConfig();
        assertThat(config.defaultModel()).isEqualTo("qwen3:14b");
        assertThat(config.models()).extracting(m -> m.id()).contains("qwen3:14b", "qwen3:8b");
        assertThat(config.allowsCustomModel()).isTrue();
        assertThat(config.available()).isTrue();
        assertThat(config.defaultProvider()).isEqualTo(ModelGateway.PROVIDER_OLLAMA);
        assertThat(config.providers()).extracting(p -> p.id()).contains(ModelGateway.PROVIDER_OLLAMA);
    }

    @Test
    void cloudConfigIncludesOllamaProvider() {
        AiConfigService service = dualProviderService(
                List.of(new ModelCatalogPort.CatalogModel("llama3.2:latest", "ollama"))
        );
        var config = service.getConfig();
        assertThat(config.providers()).extracting(p -> p.id())
                .containsExactly(ModelGateway.PROVIDER_OPENAI, ModelGateway.PROVIDER_OLLAMA);
        assertThat(config.providers().stream()
                .filter(p -> p.id().equals(ModelGateway.PROVIDER_OLLAMA))
                .findFirst()
                .orElseThrow()
                .models())
                .extracting(m -> m.id())
                .contains("llama3.2:latest");
    }

    @Test
    void resolveSelectionAcceptsOllamaProvider() {
        AiConfigService service = dualProviderService(
                List.of(new ModelCatalogPort.CatalogModel("llama3.2:latest", "ollama"))
        );
        var selection = service.resolveSelection("ollama", "llama3.2:latest");
        assertThat(selection.provider()).isEqualTo("ollama");
        assertThat(selection.model()).isEqualTo("llama3.2:latest");
    }

    private static AiConfigService service(
            String mode,
            String model,
            String allowedModels,
            List<ModelCatalogPort.CatalogModel> discovered,
            boolean available
    ) {
        ChangeAssuranceProperties properties = new ChangeAssuranceProperties(
                new ChangeAssuranceProperties.Ai(mode, true, model, null, allowedModels, null, true),
                null,
                null,
                null
        );
        ModelGateway gateway = new ModelGateway() {
            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public String defaultModel() {
                return model;
            }

            @Override
            public String defaultProvider() {
                return "ollama".equalsIgnoreCase(mode) || "local".equalsIgnoreCase(mode)
                        ? ModelGateway.PROVIDER_OLLAMA
                        : ModelGateway.PROVIDER_OPENAI;
            }

            @Override
            public <T> T execute(AiTaskType taskType, AiRequest request, Class<T> responseType) {
                throw new AiUnavailableException("not used");
            }
        };
        return new AiConfigService(properties, gateway, catalog(discovered));
    }

    private static AiConfigService dualProviderService(List<ModelCatalogPort.CatalogModel> ollamaModels) {
        ChangeAssuranceProperties properties = new ChangeAssuranceProperties(
                new ChangeAssuranceProperties.Ai("spring-ai", true, "gpt-4o-mini", null, null, null, true),
                null,
                null,
                null
        );
        ModelGateway openai = new ModelGateway() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String defaultModel() {
                return "gpt-4o-mini";
            }

            @Override
            public <T> T execute(AiTaskType taskType, AiRequest request, Class<T> responseType) {
                throw new AiUnavailableException("not used");
            }
        };
        ModelGateway ollama = new ModelGateway() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String defaultModel() {
                return "qwen3:14b";
            }

            @Override
            public <T> T execute(AiTaskType taskType, AiRequest request, Class<T> responseType) {
                throw new AiUnavailableException("not used");
            }
        };
        ModelGateway routing = new ModelGateway() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String defaultModel() {
                return "gpt-4o-mini";
            }

            @Override
            public String defaultProvider() {
                return ModelGateway.PROVIDER_OPENAI;
            }

            @Override
            public Set<String> providerIds() {
                return Set.of(ModelGateway.PROVIDER_OPENAI, ModelGateway.PROVIDER_OLLAMA);
            }

            @Override
            public ModelGateway provider(String providerId) {
                return ModelGateway.PROVIDER_OLLAMA.equals(ModelGateway.normalizeProvider(providerId))
                        ? ollama
                        : openai;
            }

            @Override
            public <T> T execute(AiTaskType taskType, AiRequest request, Class<T> responseType) {
                throw new AiUnavailableException("not used");
            }
        };
        return new AiConfigService(properties, routing, new ModelCatalogPort() {
            @Override
            public List<CatalogModel> listModels() {
                return List.of();
            }

            @Override
            public List<CatalogModel> listModelsAt(String baseUrl) {
                if (baseUrl != null && baseUrl.contains("11434")) {
                    return ollamaModels;
                }
                return List.of();
            }
        });
    }

    private static ModelCatalogPort catalog(List<ModelCatalogPort.CatalogModel> discovered) {
        return new ModelCatalogPort() {
            @Override
            public List<CatalogModel> listModels() {
                return discovered;
            }

            @Override
            public List<CatalogModel> listModelsAt(String baseUrl) {
                return discovered;
            }
        };
    }
}
