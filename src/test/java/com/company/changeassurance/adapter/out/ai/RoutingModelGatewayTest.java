package com.company.changeassurance.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.domain.exception.AiUnavailableException;
import com.company.changeassurance.domain.model.AiTaskType;

class RoutingModelGatewayTest {

    @Test
    void routesToOllamaWhenProviderOverrideSet() {
        RecordingGateway openai = new RecordingGateway("gpt-4o-mini");
        RecordingGateway ollama = new RecordingGateway("llama3.2:latest");
        RoutingModelGateway routing = new RoutingModelGateway(
                ModelGateway.PROVIDER_OPENAI,
                Map.of(
                        ModelGateway.PROVIDER_OPENAI, openai,
                        ModelGateway.PROVIDER_OLLAMA, ollama
                )
        );

        String result = routing.execute(
                AiTaskType.CHANGE_CLASSIFICATION,
                new ModelGateway.AiRequest("REV-1", "v1", "content", 1000, 500, "llama3.2:latest", "ollama"),
                String.class
        );

        assertThat(result).isEqualTo("ok");
        assertThat(ollama.calls).isEqualTo(1);
        assertThat(openai.calls).isEqualTo(0);
    }

    @Test
    void usesDefaultProviderWhenOverrideMissing() {
        RecordingGateway openai = new RecordingGateway("gpt-4o-mini");
        RecordingGateway ollama = new RecordingGateway("llama3.2:latest");
        RoutingModelGateway routing = new RoutingModelGateway(
                ModelGateway.PROVIDER_OPENAI,
                Map.of(
                        ModelGateway.PROVIDER_OPENAI, openai,
                        ModelGateway.PROVIDER_OLLAMA, ollama
                )
        );

        routing.execute(
                AiTaskType.CHANGE_CLASSIFICATION,
                new ModelGateway.AiRequest("REV-1", "v1", "content", 1000, 500),
                String.class
        );

        assertThat(openai.calls).isEqualTo(1);
        assertThat(ollama.calls).isEqualTo(0);
    }

    @Test
    void failsWhenProviderUnknown() {
        RoutingModelGateway routing = new RoutingModelGateway(
                ModelGateway.PROVIDER_OPENAI,
                Map.of(ModelGateway.PROVIDER_OPENAI, new RecordingGateway("gpt-4o-mini"))
        );
        assertThatThrownBy(() -> routing.execute(
                AiTaskType.CHANGE_CLASSIFICATION,
                new ModelGateway.AiRequest("REV-1", "v1", "content", 1000, 500, null, "ollama"),
                String.class
        )).isInstanceOf(AiUnavailableException.class);
    }

    private static final class RecordingGateway implements ModelGateway {
        private final String model;
        private int calls;

        private RecordingGateway(String model) {
            this.model = model;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String defaultModel() {
            return model;
        }

        @Override
        public <T> T execute(AiTaskType taskType, AiRequest request, Class<T> responseType) {
            calls++;
            return responseType.cast("ok");
        }
    }
}
