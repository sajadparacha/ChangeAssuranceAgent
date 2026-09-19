package com.company.changeassurance.configuration;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

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
import com.company.changeassurance.adapter.out.ai.RoutingModelGateway;
import com.company.changeassurance.adapter.out.ai.SpringAiModelGateway;
import com.company.changeassurance.adapter.out.clock.SystemClockAdapter;
import com.company.changeassurance.adapter.out.db.FakeDatabaseMetadataAdapter;
import com.company.changeassurance.adapter.out.db.OracleJdbcMetadataAdapter;
import com.company.changeassurance.application.port.out.ClockPort;
import com.company.changeassurance.application.port.out.DatabaseMetadataPort;
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
        if (properties.ai().isOpenAiCompatibleMode()) {
            return createRoutedOpenAiCompatibleGateway(properties, environment, mode);
        }
        throw new IllegalStateException(
                "Unsupported changeassurance.ai.mode='" + mode
                        + "'. Supported: fake, disabled, spring-ai, openai, chatgpt, ollama, local"
        );
    }

    @Bean
    DatabaseMetadataPort databaseMetadataPort(ChangeAssuranceProperties properties) {
        ChangeAssuranceProperties.DbMetadata db = properties.dbMetadata();
        if (db.isOracleMode()) {
            log.info("Configuring Oracle JDBC DatabaseMetadataPort defaultOwner={} url={}",
                    db.defaultOwner(), db.jdbcUrl());
            OracleJdbcMetadataAdapter adapter = new OracleJdbcMetadataAdapter(
                    db.jdbcUrl(),
                    db.username(),
                    db.password(),
                    db.driverClassName(),
                    db.defaultOwner()
            );
            adapter.verifyConnectivity();
            return adapter;
        }
        log.warn("Configuring FakeDatabaseMetadataAdapter defaultOwner={} — "
                + "package impact will NOT query live Oracle. "
                + "Set CHANGEASSURANCE_DB_METADATA_MODE=oracle for real catalog tools.",
                db.defaultOwner() == null ? "APP" : db.defaultOwner());
        return new FakeDatabaseMetadataAdapter(db.defaultOwner());
    }

    private static ModelGateway createRoutedOpenAiCompatibleGateway(
            ChangeAssuranceProperties properties,
            Environment environment,
            String mode) {
        ModelGateway primary = createOpenAiCompatibleGateway(properties, environment, mode);
        if (!properties.ai().ollamaEnabled() || !properties.ai().springAiEnabled()) {
            return primary;
        }

        Map<String, ModelGateway> providers = new LinkedHashMap<>();
        String defaultProvider;

        if (properties.ai().isLocalMode()) {
            providers.put(ModelGateway.PROVIDER_OLLAMA, primary);
            defaultProvider = ModelGateway.PROVIDER_OLLAMA;
            ModelGateway cloud = tryCreateCloudGateway(properties, environment);
            if (cloud != null && cloud.isAvailable()) {
                providers.put(ModelGateway.PROVIDER_OPENAI, cloud);
                log.info("Ollama mode with optional ChatGPT provider available");
            }
        } else {
            if (primary.isAvailable()) {
                providers.put(ModelGateway.PROVIDER_OPENAI, primary);
            }
            ModelGateway ollama = createOllamaGateway(properties);
            if (ollama != null) {
                providers.put(ModelGateway.PROVIDER_OLLAMA, ollama);
                log.info("ChatGPT mode with selectable Ollama provider baseUrl={}",
                        properties.ai().resolvedOllamaBaseUrl());
            }
            if (providers.isEmpty()) {
                return primary;
            }
            defaultProvider = providers.containsKey(ModelGateway.PROVIDER_OPENAI)
                    ? ModelGateway.PROVIDER_OPENAI
                    : ModelGateway.PROVIDER_OLLAMA;
        }
        return new RoutingModelGateway(defaultProvider, providers);
    }

    private static ModelGateway createOllamaGateway(ChangeAssuranceProperties properties) {
        String ollamaUrl = SpringAiModelGateway.normalizeBaseUrl(properties.ai().resolvedOllamaBaseUrl());
        String model = ChangeAssuranceProperties.DEFAULT_LOCAL_MODEL;
        try {
            return SpringAiModelGateway.create(
                    ChangeAssuranceProperties.LOCAL_PLACEHOLDER_API_KEY,
                    model,
                    ollamaUrl
            );
        } catch (RuntimeException ex) {
            log.warn("Failed to configure Ollama ModelGateway: {}", ex.getMessage());
            return null;
        }
    }

    private static ModelGateway tryCreateCloudGateway(
            ChangeAssuranceProperties properties,
            Environment environment) {
        String apiKey = firstNonBlank(
                environment.getProperty("spring.ai.openai.api-key"),
                environment.getProperty("OPENAI_API_KEY")
        );
        if (apiKey == null || apiKey.isBlank()
                || ChangeAssuranceProperties.LOCAL_PLACEHOLDER_API_KEY.equalsIgnoreCase(apiKey)) {
            return null;
        }
        String model = ChangeAssuranceProperties.DEFAULT_CLOUD_MODEL;
        try {
            log.info("Configuring optional ChatGPT ModelGateway model={}", model);
            return SpringAiModelGateway.create(apiKey, model, null);
        } catch (RuntimeException ex) {
            log.warn("Failed to configure optional ChatGPT ModelGateway: {}", ex.getMessage());
            return null;
        }
    }

    private static ModelGateway createOpenAiCompatibleGateway(
            ChangeAssuranceProperties properties,
            Environment environment,
            String mode) {
        String model = properties.ai().model();
        String baseUrl = firstNonBlank(
                properties.ai().baseUrl(),
                environment.getProperty("spring.ai.openai.base-url"),
                environment.getProperty("OPENAI_BASE_URL"),
                environment.getProperty("CHANGEASSURANCE_AI_BASE_URL")
        );
        if ((baseUrl == null || baseUrl.isBlank()) && properties.ai().isLocalMode()) {
            baseUrl = ChangeAssuranceProperties.DEFAULT_OLLAMA_BASE_URL;
        }
        baseUrl = SpringAiModelGateway.normalizeBaseUrl(baseUrl);

        boolean enabled = properties.ai().springAiEnabled();
        if (!enabled) {
            log.warn("AI mode={} but changeassurance.ai.spring-ai-enabled=false; model gateway unavailable", mode);
            return new SpringAiModelGateway(false, model, baseUrl, null);
        }

        String apiKey = firstNonBlank(
                environment.getProperty("spring.ai.openai.api-key"),
                environment.getProperty("OPENAI_API_KEY")
        );
        boolean localEndpoint = properties.ai().isLocalMode() || baseUrl != null;
        if ((apiKey == null || apiKey.isBlank()) && localEndpoint) {
            apiKey = ChangeAssuranceProperties.LOCAL_PLACEHOLDER_API_KEY;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI mode={} but OPENAI_API_KEY / spring.ai.openai.api-key is missing; model gateway unavailable",
                    mode);
            return new SpringAiModelGateway(false, model, baseUrl, null);
        }

        if (baseUrl != null) {
            log.info("Configuring OpenAI-compatible ModelGateway model={} baseUrl={}", model, baseUrl);
        } else {
            log.info("Configuring ChatGPT ModelGateway model={}", model);
        }
        return SpringAiModelGateway.create(apiKey, model, baseUrl);
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
