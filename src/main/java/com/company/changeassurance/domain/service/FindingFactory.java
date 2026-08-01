package com.company.changeassurance.domain.service;

import java.util.List;

import com.company.changeassurance.domain.model.EvidenceId;
import com.company.changeassurance.domain.model.Finding;
import com.company.changeassurance.domain.model.FindingCategory;
import com.company.changeassurance.domain.model.FindingId;
import com.company.changeassurance.domain.model.FindingSeverity;
import com.company.changeassurance.domain.model.FindingStatus;
import com.company.changeassurance.domain.model.ToolType;

public final class FindingFactory {

    private FindingFactory() {
    }

    public static Finding deterministic(
            String findingId,
            String ruleCode,
            String title,
            String description,
            FindingSeverity severity,
            FindingCategory category,
            List<EvidenceId> evidenceIds,
            String requiredAction,
            ToolType sourceTool
    ) {
        return new Finding(
                new FindingId(findingId),
                ruleCode,
                title,
                description,
                severity,
                category,
                evidenceIds,
                requiredAction,
                true,
                FindingStatus.OPEN,
                sourceTool
        );
    }
}
