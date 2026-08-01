package com.company.changeassurance.configuration;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.company.changeassurance.adapter.out.ai.DisabledModelGateway;
import com.company.changeassurance.adapter.out.ai.FakeModelGateway;
import com.company.changeassurance.adapter.out.ai.SpringAiModelGateway;
import com.company.changeassurance.adapter.out.clock.SystemClockAdapter;
import com.company.changeassurance.application.port.out.ClockPort;
import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.domain.policy.DefaultRecommendationPolicy;
import com.company.changeassurance.domain.policy.RecommendationPolicy;
import com.company.changeassurance.domain.policy.RiskScoringConfig;

@Configuration
@EnableConfigurationProperties(ChangeAssuranceProperties.class)
public class ChangeAssuranceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChangeAssuranceConfiguration.class);

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    ClockPort clockPort(Clock utcClock) {
        return new SystemClockAdapter(utcClock);
    }

    @Bean
    ModelGateway modelGateway(ChangeAssuranceProperties properties, Environment environment) {
        String mode = properties.ai().mode();
        if (mode == null || mode.isBlank() || "fake".equalsIgnoreCase(mode)) {
            return new FakeModelGateway();
        }
        if ("disabled".equalsIgnoreCase(mode)) {
            return new DisabledModelGateway();
        }
        if ("spring-ai".equalsIgnoreCase(mode) || "openai".equalsIgnoreCase(mode) || "chatgpt".equalsIgnoreCase(mode)) {
            String apiKey = firstNonBlank(
                    environment.getProperty("spring.ai.openai.api-key"),
                    environment.getProperty("OPENAI_API_KEY")
            );
            boolean enabled = properties.ai().springAiEnabled();
            String model = properties.ai().model();
            if (!enabled) {
                log.warn("AI mode={} but changeassurance.ai.spring-ai-enabled=false; ChatGPT unavailable", mode);
                return new SpringAiModelGateway(false, model);
            }
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("AI mode={} but OPENAI_API_KEY / spring.ai.openai.api-key is missing; ChatGPT unavailable",
                        mode);
                return new SpringAiModelGateway(false, model);
            }
            log.info("Configuring ChatGPT ModelGateway model={}", model);
            return SpringAiModelGateway.create(apiKey, model);
        }
        throw new IllegalStateException(
                "Unsupported changeassurance.ai.mode='" + mode
                        + "'. Supported: fake, disabled, spring-ai, openai, chatgpt"
        );
    }

    @Bean
    RiskScoringConfig riskScoringConfig() {
        return RiskScoringConfig.defaults();
    }

    @Bean
    RecommendationPolicy recommendationPolicy(RiskScoringConfig config) {
        return new DefaultRecommendationPolicy(config);
    }

    @Bean
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
            }
        };
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
