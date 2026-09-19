package com.company.changeassurance.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChangeAssurancePropertiesTest {

    @Test
    void cloudDefaults() {
        ChangeAssuranceProperties.Ai ai = new ChangeAssuranceProperties.Ai(
                "spring-ai", true, null, null, null, null, true);
        assertThat(ai.model()).isEqualTo(ChangeAssuranceProperties.DEFAULT_CLOUD_MODEL);
        assertThat(ai.baseUrl()).isNull();
        assertThat(ai.isLocalMode()).isFalse();
        assertThat(ai.isOpenAiCompatibleMode()).isTrue();
        assertThat(ai.allowedModelList()).isEmpty();
    }

    @Test
    void ollamaDefaultsToQwenAndLocalBaseUrl() {
        ChangeAssuranceProperties.Ai ai = new ChangeAssuranceProperties.Ai(
                "ollama", true, null, null, null, null, true);
        assertThat(ai.model()).isEqualTo(ChangeAssuranceProperties.DEFAULT_LOCAL_MODEL);
        assertThat(ai.baseUrl()).isEqualTo(ChangeAssuranceProperties.DEFAULT_OLLAMA_BASE_URL);
        assertThat(ai.isLocalMode()).isTrue();
    }

    @Test
    void localModeRespectsExplicitModelAndBaseUrl() {
        ChangeAssuranceProperties.Ai ai = new ChangeAssuranceProperties.Ai(
                "local",
                true,
                "qwen3:8b",
                "http://localhost:1234/v1",
                "qwen3:8b,qwen3:14b",
                null,
                true
        );
        assertThat(ai.model()).isEqualTo("qwen3:8b");
        assertThat(ai.baseUrl()).isEqualTo("http://localhost:1234/v1");
        assertThat(ai.allowedModelList()).containsExactly("qwen3:8b", "qwen3:14b");
    }

    @Test
    void dbMetadataDefaultsToFake() {
        ChangeAssuranceProperties properties = new ChangeAssuranceProperties(null, null, null, null);
        assertThat(properties.dbMetadata().mode()).isEqualTo("fake");
        assertThat(properties.dbMetadata().defaultOwner()).isEqualTo("APP");
        assertThat(properties.dbMetadata().isFakeMode()).isTrue();
        assertThat(properties.investigation().maxFollowUpRounds()).isEqualTo(2);
    }
}
