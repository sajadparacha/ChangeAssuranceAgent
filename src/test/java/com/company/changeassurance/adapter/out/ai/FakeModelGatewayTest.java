package com.company.changeassurance.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.domain.model.AiTaskType;

class FakeModelGatewayTest {

    @Test
    void returnsRegisteredResponseByType() {
        FakeModelGateway gateway = new FakeModelGateway();
        gateway.register(String.class, "classified");

        String result = gateway.execute(
                AiTaskType.CHANGE_CLASSIFICATION,
                new ModelGateway.AiRequest("REV-1", "prompt-v1", "content", 1000, 500),
                String.class
        );

        assertThat(result).isEqualTo("classified");
        assertThat(gateway.isAvailable()).isTrue();
    }

    @Test
    void returnsRegisteredResponseByTaskType() {
        FakeModelGateway gateway = new FakeModelGateway();
        gateway.register(AiTaskType.REVIEW_PLANNING, () -> "plan");

        String result = gateway.execute(
                AiTaskType.REVIEW_PLANNING,
                new ModelGateway.AiRequest("REV-1", "prompt-v1", "content", 1000, 500),
                String.class
        );

        assertThat(result).isEqualTo("plan");
    }

    @Test
    void failsWhenNoResponseRegistered() {
        FakeModelGateway gateway = new FakeModelGateway();

        assertThatThrownBy(() -> gateway.execute(
                AiTaskType.CRITIC_REVIEW,
                new ModelGateway.AiRequest("REV-1", "prompt-v1", "content", 1000, 500),
                String.class
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No fake response registered");
    }
}
