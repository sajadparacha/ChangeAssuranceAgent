package com.company.changeassurance.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.domain.exception.AiUnavailableException;
import com.company.changeassurance.domain.model.AiTaskType;

class DisabledModelGatewayTest {

    @Test
    void isUnavailableAndThrowsOnExecute() {
        DisabledModelGateway gateway = new DisabledModelGateway();

        assertThat(gateway.isAvailable()).isFalse();
        assertThatThrownBy(() -> gateway.execute(
                AiTaskType.DRAFT_REPORT_GENERATION,
                new ModelGateway.AiRequest("REV-1", "prompt-v1", "content", 1000, 500),
                String.class
        ))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("AI is disabled");
    }
}
