package com.company.changeassurance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.test.context.TestPropertySource;

import com.company.changeassurance.application.port.out.ClockPort;
import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.adapter.out.ai.FakeModelGateway;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "changeassurance.ai.mode=fake")
class ChangeAssuranceApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ModelGateway modelGateway;

    @Autowired
    private ClockPort clockPort;

    @Test
    void contextLoads() {
        assertThat(modelGateway).isInstanceOf(FakeModelGateway.class);
        assertThat(modelGateway.isAvailable()).isTrue();
        assertThat(clockPort.now()).isNotNull();
    }

    @Test
    void actuatorHealthIsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
