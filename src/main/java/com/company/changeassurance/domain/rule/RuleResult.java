package com.company.changeassurance.domain.rule;

import java.util.List;
import java.util.Objects;

import com.company.changeassurance.domain.model.EvidenceId;
import com.company.changeassurance.domain.model.FindingCategory;
import com.company.changeassurance.domain.model.FindingSeverity;

/**
 * Outcome of evaluating a single deterministic {@link ChangeReviewRule}.
 */
public record RuleResult(
        String ruleCode,
        String ruleName,
        boolean triggered,
        FindingSeverity severity,
        FindingCategory category,
        String description,
        String requiredAction,
        double weight,
        List<EvidenceId> evidenceIds
) {

    public RuleResult {
        Objects.requireNonNull(ruleCode, "ruleCode must not be null");
        Objects.requireNonNull(ruleName, "ruleName must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(description, "description must not be null");
        evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds must not be null"));
    }

    public static RuleResult notTriggered(
            String ruleCode,
            String ruleName,
            FindingSeverity severity,
            FindingCategory category,
            String description
    ) {
        return new RuleResult(
                ruleCode,
                ruleName,
                false,
                severity,
                category,
                description,
                null,
                0.0d,
                List.of()
        );
    }
}
