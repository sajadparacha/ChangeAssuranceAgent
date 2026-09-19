package com.company.changeassurance.application.port.out;

import java.util.List;

/**
 * Discovers models from an OpenAI-compatible provider (ChatGPT, Ollama, LM Studio).
 */
public interface ModelCatalogPort {

    /**
     * @return provider-reported models for the active/default endpoint; empty when discovery is unavailable
     */
    List<CatalogModel> listModels();

    /**
     * Lists models from an explicit OpenAI-compatible base URL (e.g. Ollama at {@code http://localhost:11434/v1}).
     *
     * @return provider-reported models; empty when discovery is unavailable
     */
    default List<CatalogModel> listModelsAt(String baseUrl) {
        return List.of();
    }

    record CatalogModel(String id, String ownedBy) {
        public CatalogModel {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id must not be blank");
            }
            if (ownedBy == null || ownedBy.isBlank()) {
                ownedBy = "unknown";
            }
        }
    }
}
