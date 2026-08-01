package com.company.changeassurance.adapter.out.ai;

import java.util.List;

import com.company.changeassurance.domain.model.ChangeClassification;
import com.company.changeassurance.domain.model.ChangeType;
import com.company.changeassurance.domain.model.Complexity;
import com.company.changeassurance.domain.model.EvidenceId;
import com.company.changeassurance.domain.model.ReviewCapability;

/**
 * Jackson-friendly shapes returned by ChatGPT for controlled AI tasks.
 */
final class AiStructuredResponses {

    private AiStructuredResponses() {
    }

    record ClassificationResponse(
            ChangeType primaryChangeType,
            List<ChangeType> secondaryChangeTypes,
            Complexity complexity,
            double confidence,
            List<ReviewCapability> requiredReviewCapabilities,
            List<String> evidenceIds
    ) {
        ChangeClassification toDomain() {
            List<ChangeType> secondary = secondaryChangeTypes == null ? List.of() : secondaryChangeTypes;
            List<ReviewCapability> capabilities =
                    requiredReviewCapabilities == null ? List.of() : requiredReviewCapabilities;
            List<EvidenceId> ids = evidenceIds == null
                    ? List.of()
                    : evidenceIds.stream()
                            .filter(id -> id != null && !id.isBlank())
                            .map(EvidenceId::new)
                            .toList();
            ChangeType primary = primaryChangeType == null ? ChangeType.UNKNOWN : primaryChangeType;
            Complexity level = complexity == null ? Complexity.UNKNOWN : complexity;
            double conf = Math.max(0.0d, Math.min(1.0d, confidence));
            return new ChangeClassification(primary, secondary, level, conf, capabilities, ids);
        }
    }

    record GapQuestionsResponse(List<String> questions) {
        List<String> safeQuestions() {
            if (questions == null) {
                return List.of();
            }
            return questions.stream()
                    .filter(q -> q != null && !q.isBlank())
                    .map(String::trim)
                    .limit(5)
                    .toList();
        }
    }
}
