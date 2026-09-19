package com.company.changeassurance.configuration;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "changeassurance")
public record ChangeAssuranceProperties(Ai ai, Storage storage, DbMetadata dbMetadata, Investigation investigation) {

    public static final String DEFAULT_CLOUD_MODEL = "gpt-4o-mini";
    public static final String DEFAULT_LOCAL_MODEL = "qwen3:14b";
    public static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434/v1";
    public static final String LOCAL_PLACEHOLDER_API_KEY = "ollama";

    public ChangeAssuranceProperties {
        if (ai == null) {
            ai = new Ai("spring-ai", true, DEFAULT_CLOUD_MODEL, null, null, DEFAULT_OLLAMA_BASE_URL, true);
        }
        if (storage == null) {
            storage = new Storage("./uploads", 1_048_576L);
        }
        if (dbMetadata == null) {
            dbMetadata = DbMetadata.defaults();
        }
        if (investigation == null) {
            investigation = Investigation.defaults();
        }
    }

    public record Ai(
            String mode,
            boolean springAiEnabled,
            String model,
            String baseUrl,
            String allowedModels,
            String ollamaBaseUrl,
            boolean ollamaEnabled
    ) {
        public Ai {
            if (mode == null || mode.isBlank()) {
                mode = "spring-ai";
            }
            boolean local = isLocalMode(mode);
            if (model == null || model.isBlank() || "none".equalsIgnoreCase(model)) {
                model = local ? DEFAULT_LOCAL_MODEL : DEFAULT_CLOUD_MODEL;
            }
            if ((baseUrl == null || baseUrl.isBlank()) && local) {
                baseUrl = DEFAULT_OLLAMA_BASE_URL;
            } else if (baseUrl != null && baseUrl.isBlank()) {
                baseUrl = null;
            }
            if (allowedModels != null && allowedModels.isBlank()) {
                allowedModels = null;
            }
            if (ollamaBaseUrl == null || ollamaBaseUrl.isBlank()) {
                ollamaBaseUrl = DEFAULT_OLLAMA_BASE_URL;
            }
        }

        public List<String> allowedModelList() {
            if (allowedModels == null || allowedModels.isBlank()) {
                return List.of();
            }
            return Arrays.stream(allowedModels.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        public boolean isLocalMode() {
            return isLocalMode(mode);
        }

        public boolean isOpenAiCompatibleMode() {
            return isOpenAiCompatibleMode(mode);
        }

        /** Prefer explicit Ollama URL; fall back to local base-url when mode is ollama/local. */
        public String resolvedOllamaBaseUrl() {
            if (isLocalMode() && baseUrl != null && !baseUrl.isBlank()) {
                return baseUrl;
            }
            return ollamaBaseUrl == null || ollamaBaseUrl.isBlank() ? DEFAULT_OLLAMA_BASE_URL : ollamaBaseUrl;
        }

        static boolean isLocalMode(String mode) {
            return "ollama".equalsIgnoreCase(mode) || "local".equalsIgnoreCase(mode);
        }

        static boolean isOpenAiCompatibleMode(String mode) {
            return isLocalMode(mode)
                    || "spring-ai".equalsIgnoreCase(mode)
                    || "openai".equalsIgnoreCase(mode)
                    || "chatgpt".equalsIgnoreCase(mode);
        }
    }

    public record Storage(String root, long maxBytes) {
        public Storage {
            if (root == null || root.isBlank()) {
                root = "./uploads";
            }
            if (maxBytes <= 0) {
                maxBytes = 1_048_576L;
            }
        }
    }

    /**
     * Database catalog metadata for package impact analysis.
     * mode: {@code fake} (default) or {@code oracle}.
     */
    public record DbMetadata(
            String mode,
            String jdbcUrl,
            String username,
            String password,
            String driverClassName,
            String defaultOwner
    ) {
        public DbMetadata {
            if (mode == null || mode.isBlank()) {
                mode = "fake";
            }
            if (driverClassName != null && driverClassName.isBlank()) {
                driverClassName = null;
            }
            if (defaultOwner != null && defaultOwner.isBlank()) {
                defaultOwner = null;
            }
            if (jdbcUrl != null && jdbcUrl.isBlank()) {
                jdbcUrl = null;
            }
            if (username != null && username.isBlank()) {
                username = null;
            }
            if (password != null && password.isBlank()) {
                password = null;
            }
        }

        public static DbMetadata defaults() {
            return new DbMetadata("fake", null, null, null, null, "APP");
        }

        public boolean isOracleMode() {
            return "oracle".equalsIgnoreCase(mode);
        }

        public boolean isFakeMode() {
            return !isOracleMode();
        }
    }

    /**
     * Bounded agentic investigation settings for package impact analysis.
     */
    public record Investigation(
            int maxFollowUpRounds,
            int largeDependentThreshold,
            int transitiveMaxDepth,
            int transitiveMaxNodes,
            int sourceSearchMaxRows
    ) {
        public Investigation {
            if (maxFollowUpRounds < 0) {
                maxFollowUpRounds = 0;
            }
            if (largeDependentThreshold <= 0) {
                largeDependentThreshold = 4;
            }
            if (transitiveMaxDepth < 2) {
                transitiveMaxDepth = 3;
            }
            if (transitiveMaxNodes <= 0) {
                transitiveMaxNodes = 50;
            }
            if (sourceSearchMaxRows <= 0) {
                sourceSearchMaxRows = 100;
            }
        }

        public static Investigation defaults() {
            return new Investigation(2, 4, 3, 50, 100);
        }
    }
}
