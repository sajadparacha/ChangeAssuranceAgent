package com.company.changeassurance.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.domain.exception.AiUnavailableException;
import com.company.changeassurance.domain.model.AiTaskType;

class SpringAiModelGatewayTest {

    @Test
    void unavailableWithoutClient() {
        SpringAiModelGateway gateway = new SpringAiModelGateway(true, "gpt-4o-mini");
        assertThat(gateway.isAvailable()).isFalse();
        assertThatThrownBy(() -> gateway.execute(
                AiTaskType.CHANGE_CLASSIFICATION,
                new ModelGateway.AiRequest("REV-1", "caa-prompt-v1", "content", 1000, 500),
                String.class
        )).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void createRejectsBlankApiKey() {
        assertThatThrownBy(() -> SpringAiModelGateway.create("  ", "gpt-4o-mini"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithLocalBaseUrlSucceeds() {
        SpringAiModelGateway gateway = SpringAiModelGateway.create(
                "ollama",
                "qwen3:14b",
                "http://localhost:11434/v1/"
        );
        assertThat(gateway.isAvailable()).isTrue();
        assertThat(gateway.modelIdentifier()).isEqualTo("qwen3:14b");
        assertThat(gateway.baseUrl()).isEqualTo("http://localhost:11434/v1");
    }

    @Test
    void normalizeBaseUrlStripsTrailingSlashes() {
        assertThat(SpringAiModelGateway.normalizeBaseUrl("http://localhost:1234/v1///"))
                .isEqualTo("http://localhost:1234/v1");
        assertThat(SpringAiModelGateway.normalizeBaseUrl("  ")).isNull();
        assertThat(SpringAiModelGateway.normalizeBaseUrl(null)).isNull();
    }
}
