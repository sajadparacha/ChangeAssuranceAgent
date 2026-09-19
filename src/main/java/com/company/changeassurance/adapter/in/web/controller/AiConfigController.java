package com.company.changeassurance.adapter.in.web.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.changeassurance.application.port.in.GetAiConfigUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/ai")
@CrossOrigin(origins = "*")
@Tag(name = "AI Configuration")
public class AiConfigController {

    private final GetAiConfigUseCase getAiConfigUseCase;

    public AiConfigController(GetAiConfigUseCase getAiConfigUseCase) {
        this.getAiConfigUseCase = getAiConfigUseCase;
    }

    @GetMapping("/config")
    @Operation(operationId = "getAiConfig")
    public Map<String, Object> config() {
        GetAiConfigUseCase.AiConfigResult config = getAiConfigUseCase.getConfig();
        List<Map<String, Object>> models = config.models().stream()
                .map(this::toModel)
                .toList();
        List<Map<String, Object>> providers = config.providers().stream()
                .map(provider -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", provider.id());
                    row.put("label", provider.label());
                    row.put("available", provider.available());
                    row.put("baseUrl", provider.baseUrl() == null ? "" : provider.baseUrl());
                    row.put("defaultModel", provider.defaultModel());
                    row.put("allowsCustomModel", provider.allowsCustomModel());
                    row.put("models", provider.models().stream().map(this::toModel).toList());
                    return row;
                })
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("available", config.available());
        body.put("mode", config.mode());
        body.put("defaultProvider", config.defaultProvider() == null ? "" : config.defaultProvider());
        body.put("defaultModel", config.defaultModel());
        body.put("baseUrl", config.baseUrl() == null ? "" : config.baseUrl());
        body.put("allowsCustomModel", config.allowsCustomModel());
        body.put("models", models);
        body.put("providers", providers);
        return body;
    }

    private Map<String, Object> toModel(GetAiConfigUseCase.ModelOption model) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", model.id());
        row.put("label", model.label());
        row.put("isDefault", model.isDefault());
        return row;
    }
}
