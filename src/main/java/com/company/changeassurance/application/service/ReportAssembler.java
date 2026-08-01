package com.company.changeassurance.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.Finding;
import com.company.changeassurance.domain.model.FindingSeverity;

@Service
public class ReportAssembler {

    public Map<String, Object> assemble(ChangeReview review) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("identification", Map.of(
                "reviewId", review.getReviewId().value(),
                "application", review.getApplicationName(),
                "changeTitle", review.getChangeTitle(),
                "environment", nullToEmpty(review.getTargetEnvironment()),
                "createdAt", review.getCreatedAt().toString(),
                "completedAt", review.getCompletedAt() == null ? "" : review.getCompletedAt().toString()
        ));
        report.put("executiveSummary", review.getAiAssessment() == null
                ? "Deterministic first-pass review complete. Human approval is mandatory."
                : review.getAiAssessment().executiveSummary());
        report.put("agentUnderstanding", Map.of(
                "classification", review.getChangeClassification(),
                "affectedObjects", review.getAffectedObjects(),
                "aiAvailability", review.getAiAssessment() == null
                        ? "UNAVAILABLE"
                        : review.getAiAssessment().availabilityStatus()
        ));
        report.put("reviewPlan", review.getReviewPlan());
        report.put("findingsBySeverity", groupFindings(review.getFindings()));
        report.put("informationGaps", review.getInformationGaps());
        report.put("riskScenarios", review.getRiskScenarios());
        report.put("riskAssessment", review.getRiskAssessment());
        report.put("recommendation", Map.of(
                "value", review.getReadinessRecommendation() == null
                        ? "INSUFFICIENT_INFORMATION"
                        : review.getReadinessRecommendation().name(),
                "deterministicReasonCodes", review.getDeterministicReasonCodes()
        ));
        report.put("requiredActions", review.getFindings().stream()
                .map(Finding::requiredAction)
                .filter(a -> a != null && !a.isBlank())
                .distinct()
                .toList());
        report.put("suggestedTests", review.getAiAssessment() == null
                ? List.of()
                : review.getAiAssessment().suggestedTests());
        report.put("humanReviewQuestions", review.getAiAssessment() == null
                ? List.of()
                : review.getAiAssessment().humanReviewQuestions());
        report.put("criticReview", review.getCriticResult());
        report.put("evidenceReferences", review.getEvidence());
        report.put("limitations", List.of(
                "No SQL was executed",
                "No production system was accessed",
                "No Oracle metadata was queried",
                "Conclusions depend on submitted evidence",
                "Human approval remains mandatory"
        ));
        report.put("humanApprovalRequired", true);
        report.put("status", review.getReviewStatus());
        report.put("currentStage", review.getCurrentStage());
        return report;
    }

    private Map<String, List<Finding>> groupFindings(List<Finding> findings) {
        return findings.stream().collect(Collectors.groupingBy(f -> f.severity().name()));
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
