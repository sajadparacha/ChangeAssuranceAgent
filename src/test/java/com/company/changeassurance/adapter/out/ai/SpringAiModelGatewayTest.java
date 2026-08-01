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
}
