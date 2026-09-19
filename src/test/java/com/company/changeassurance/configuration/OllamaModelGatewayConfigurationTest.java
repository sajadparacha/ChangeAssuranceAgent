package com.company.changeassurance.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.company.changeassurance.adapter.out.ai.RoutingModelGateway;
import com.company.changeassurance.adapter.out.ai.SpringAiModelGateway;
import com.company.changeassurance.application.port.out.ModelGateway;

@SpringBootTest(properties = {
        "changeassurance.ai.mode=ollama",
        "changeassurance.ai.model=",
        "changeassurance.ai.base-url=",
        "changeassurance.ai.ollama-enabled=true",
        "changeassurance.db-metadata.mode=fake"
})
class OllamaModelGatewayConfigurationTest {

    @Autowired
    private ModelGateway modelGateway;

    @Autowired
    private ChangeAssuranceProperties properties;

    @Test
    void wiresAvailableLocalGatewayWithQwenDefaults() {
        assertThat(properties.ai().mode()).isEqualTo("ollama");
        assertThat(properties.ai().model()).isEqualTo(ChangeAssuranceProperties.DEFAULT_LOCAL_MODEL);
        assertThat(properties.ai().baseUrl()).isEqualTo(ChangeAssuranceProperties.DEFAULT_OLLAMA_BASE_URL);
        assertThat(modelGateway.isAvailable()).isTrue();
        assertThat(modelGateway.defaultProvider()).isEqualTo(ModelGateway.PROVIDER_OLLAMA);

        ModelGateway ollama = modelGateway instanceof RoutingModelGateway routing
                ? routing.provider(ModelGateway.PROVIDER_OLLAMA)
                : modelGateway;
        assertThat(ollama).isInstanceOf(SpringAiModelGateway.class);
        SpringAiModelGateway gateway = (SpringAiModelGateway) ollama;
        assertThat(gateway.modelIdentifier()).isEqualTo(ChangeAssuranceProperties.DEFAULT_LOCAL_MODEL);
        assertThat(gateway.baseUrl()).isEqualTo(ChangeAssuranceProperties.DEFAULT_OLLAMA_BASE_URL);
    }
}
