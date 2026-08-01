package com.company.changeassurance.application.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.company.changeassurance.domain.model.ToolType;
import com.company.changeassurance.domain.rule.AssuranceTool;

@Component
public class ToolRegistry {

    private final Map<ToolType, AssuranceTool> toolsByType;

    public ToolRegistry(List<AssuranceTool> tools) {
        this.toolsByType = tools.stream()
                .collect(Collectors.toMap(AssuranceTool::type, Function.identity()));
    }

    public AssuranceTool require(ToolType type) {
        AssuranceTool tool = toolsByType.get(type);
        if (tool == null) {
            throw new IllegalArgumentException("Tool not registered: " + type);
        }
        return tool;
    }

    public boolean isApproved(ToolType type) {
        return toolsByType.containsKey(type);
    }

    public List<ToolType> approvedTools() {
        return List.copyOf(toolsByType.keySet());
    }
}
