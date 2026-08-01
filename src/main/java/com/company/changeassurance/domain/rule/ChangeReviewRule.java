package com.company.changeassurance.domain.rule;

import java.util.List;

import com.company.changeassurance.domain.model.AffectedObject;
import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.Evidence;
import com.company.changeassurance.domain.sql.SqlParseResult;

/**
 * Extensible deterministic review rule. New rules register as beans without changing the workflow.
 */
public interface ChangeReviewRule {

    String code();

    String name();

    RuleResult evaluate(ChangeReviewContext context);

    /**
     * Shared evaluation context for rules and tools.
     */
    record ChangeReviewContext(
            ChangeReview review,
            String deploymentPlan,
            String rollbackPlan,
            String testEvidence,
            String sqlContent,
            String sqlFilename,
            SqlParseResult sqlParseResult,
            List<Evidence> evidenceCatalog,
            List<AffectedObject> affectedObjects
    ) {
        public ChangeReviewContext {
            if (review == null) {
                throw new IllegalArgumentException("review must not be null");
            }
            evidenceCatalog = evidenceCatalog == null ? List.of() : List.copyOf(evidenceCatalog);
            affectedObjects = affectedObjects == null ? List.of() : List.copyOf(affectedObjects);
        }

        public static ChangeReviewContext of(ChangeReview review) {
            return new ChangeReviewContext(
                    review,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    review.getEvidence(),
                    review.getAffectedObjects()
            );
        }
    }
}
