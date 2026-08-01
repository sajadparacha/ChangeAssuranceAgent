package com.company.changeassurance.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "changeassurance")
public record ChangeAssuranceProperties(Ai ai, Storage storage) {

    public ChangeAssuranceProperties {
        if (ai == null) {
            ai = new Ai("spring-ai", true, "gpt-4o-mini");
        }
        if (storage == null) {
            storage = new Storage("./uploads", 1_048_576L);
        }
    }

    public record Ai(String mode, boolean springAiEnabled, String model) {
        public Ai {
            if (mode == null || mode.isBlank()) {
                mode = "spring-ai";
            }
            if (model == null || model.isBlank() || "none".equalsIgnoreCase(model)) {
                model = "gpt-4o-mini";
            }
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
}
