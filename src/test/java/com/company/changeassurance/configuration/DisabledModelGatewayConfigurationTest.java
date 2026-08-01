package com.company.changeassurance.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.company.changeassurance.adapter.out.ai.DisabledModelGateway;
import com.company.changeassurance.application.port.out.ModelGateway;

@SpringBootTest
@TestPropertySource(properties = "changeassurance.ai.mode=disabled")
class DisabledModelGatewayConfigurationTest {

    @Autowired
    private ModelGateway modelGateway;

    @Test
    void wiresDisabledGatewayWhenConfigured() {
        assertThat(modelGateway).isInstanceOf(DisabledModelGateway.class);
        assertThat(modelGateway.isAvailable()).isFalse();
    }
}
