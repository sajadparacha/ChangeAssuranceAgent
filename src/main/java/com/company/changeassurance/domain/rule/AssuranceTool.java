package com.company.changeassurance.domain.rule;

import com.company.changeassurance.domain.model.ToolType;

/**
 * Extensible deterministic assurance tool. Implementations register as Spring beans in later phases.
 */
public interface AssuranceTool {

    ToolType type();

    ToolExecutionResult execute(ToolExecutionRequest request);
}
