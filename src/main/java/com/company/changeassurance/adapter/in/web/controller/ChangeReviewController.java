package com.company.changeassurance.adapter.in.web.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.company.changeassurance.application.port.in.SubmitChangeReviewUseCase;
import com.company.changeassurance.application.port.in.SubmitClarificationAnswerUseCase;
import com.company.changeassurance.application.port.out.ActivityLogRepository;
import com.company.changeassurance.application.service.HtmlImpactReportRenderer;
import com.company.changeassurance.application.workflow.ChangeAssuranceWorkflowService;
import com.company.changeassurance.domain.exception.DomainValidationException;
import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.ReviewId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/change-reviews")
@CrossOrigin(origins = "*")
@Tag(name = "Change Reviews")
public class ChangeReviewController {

    private final ChangeAssuranceWorkflowService workflowService;
    private final ActivityLogRepository activityLogRepository;
    private final HtmlImpactReportRenderer htmlImpactReportRenderer;

    public ChangeReviewController(
            ChangeAssuranceWorkflowService workflowService,
            ActivityLogRepository activityLogRepository,
            HtmlImpactReportRenderer htmlImpactReportRenderer
    ) {
        this.workflowService = workflowService;
        this.activityLogRepository = activityLogRepository;
        this.htmlImpactReportRenderer = htmlImpactReportRenderer;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "submitChangeReview")
    public ResponseEntity<Map<String, Object>> submit(
            @RequestParam(value = "applicationName", required = false) String applicationName,
            @RequestParam(value = "changeTitle", required = false) String changeTitle,
            @RequestParam(value = "changeDescription", required = false) String changeDescription,
            @RequestParam(value = "changeType", required = false) String changeType,
            @RequestParam(value = "targetEnvironment", required = false) String targetEnvironment,
            @RequestParam(value = "implementationWindow", required = false) String implementationWindow,
            @RequestParam(value = "deploymentPlan", required = false) String deploymentPlan,
            @RequestParam(value = "rollbackPlan", required = false) String rollbackPlan,
            @RequestParam(value = "testEvidence", required = false) String testEvidence,
            @RequestParam(value = "aiModel", required = false) String aiModel,
            @RequestParam(value = "aiProvider", required = false) String aiProvider,
            @RequestParam(value = "packageName", required = false) String packageName,
            @RequestParam(value = "schemaOwner", required = false) String schemaOwner,
            @RequestPart(value = "sqlFile", required = false) MultipartFile sqlFile
    ) throws Exception {
        byte[] sqlBytes = null;
        String sqlName = null;
        if (sqlFile != null && !sqlFile.isEmpty()) {
            sqlBytes = sqlFile.getBytes();
            sqlName = sqlFile.getOriginalFilename();
        }
        var result = workflowService.submit(new SubmitChangeReviewUseCase.SubmitChangeReviewCommand(
                applicationName,
                changeTitle,
                changeDescription,
                changeType,
                targetEnvironment,
                implementationWindow,
                deploymentPlan,
                rollbackPlan,
                testEvidence,
                sqlName,
                sqlBytes,
                aiModel,
                aiProvider,
                packageName,
                schemaOwner
        ));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "reviewId", result.reviewId().value(),
                "status", result.status().name(),
                "currentStage", result.currentStage().name()
        ));
    }

    @GetMapping
    @Operation(operationId = "listChangeReviews")
    public List<Map<String, Object>> list() {
        return workflowService.listReviews().stream().map(this::toSummary).toList();
    }

    @GetMapping("/{reviewId}")
    @Operation(operationId = "getChangeReview")
    public Map<String, Object> get(@PathVariable String reviewId) {
        return toDetail(require(reviewId));
    }

    @GetMapping("/{reviewId}/plan")
    @Operation(operationId = "getChangeReviewPlan")
    public Object plan(@PathVariable String reviewId) {
        return require(reviewId).getReviewPlan();
    }

    @GetMapping("/{reviewId}/activities")
    @Operation(operationId = "getChangeReviewActivities")
    public Object activities(@PathVariable String reviewId) {
        return activityLogRepository.findToolActivitiesByReviewId(new ReviewId(reviewId));
    }

    @GetMapping("/{reviewId}/findings")
    @Operation(operationId = "getChangeReviewFindings")
    public Object findings(@PathVariable String reviewId) {
        return require(reviewId).getFindings();
    }

    @GetMapping("/{reviewId}/information-gaps")
    @Operation(operationId = "getChangeReviewInformationGaps")
    public Object gaps(@PathVariable String reviewId) {
        return require(reviewId).getInformationGaps();
    }

    @PostMapping("/{reviewId}/answers")
    @Operation(operationId = "submitClarificationAnswer")
    public ResponseEntity<Void> answer(
            @PathVariable String reviewId,
            @RequestBody Map<String, String> body
    ) {
        workflowService.submit(
                new ReviewId(reviewId),
                new SubmitClarificationAnswerUseCase.SubmitClarificationAnswerCommand(
                        body.get("gapId"),
                        body.get("answer")
                )
        );
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{reviewId}/report")
    @Operation(operationId = "getChangeReviewReport")
    public Object report(@PathVariable String reviewId) {
        return workflowService.getReport(new ReviewId(reviewId));
    }

    @GetMapping(value = "/{reviewId}/report.html", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(operationId = "getChangeReviewHtmlReport")
    public ResponseEntity<String> reportHtml(@PathVariable String reviewId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> report = (Map<String, Object>) workflowService.getReport(new ReviewId(reviewId));
        String html = htmlImpactReportRenderer.render(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"impact-report-" + reviewId + ".html\"")
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private ChangeReview require(String reviewId) {
        return workflowService.getById(new ReviewId(reviewId))
                .orElseThrow(() -> new DomainValidationException("Review not found: " + reviewId));
    }

    private Map<String, Object> toSummary(ChangeReview review) {
        return Map.of(
                "reviewId", review.getReviewId().value(),
                "applicationName", review.getApplicationName(),
                "changeTitle", review.getChangeTitle(),
                "createdAt", review.getCreatedAt().toString(),
                "status", review.getReviewStatus().name(),
                "riskLevel", review.getRiskAssessment() == null ? "UNKNOWN" : review.getRiskAssessment().riskLevel().name(),
                "recommendation", review.getReadinessRecommendation() == null
                        ? ""
                        : review.getReadinessRecommendation().name()
        );
    }

    private Map<String, Object> toDetail(ChangeReview review) {
        return Map.ofEntries(
                Map.entry("reviewId", review.getReviewId().value()),
                Map.entry("applicationName", review.getApplicationName()),
                Map.entry("changeTitle", review.getChangeTitle()),
                Map.entry("changeDescription", nullSafe(review.getChangeDescription())),
                Map.entry("targetEnvironment", nullSafe(review.getTargetEnvironment())),
                Map.entry("implementationWindow", nullSafe(review.getImplementationWindow())),
                Map.entry("status", review.getReviewStatus().name()),
                Map.entry("currentStage", review.getCurrentStage().name()),
                Map.entry("classification", review.getChangeClassification() == null ? Map.of() : review.getChangeClassification()),
                Map.entry("affectedObjects", review.getAffectedObjects()),
                Map.entry("findings", review.getFindings()),
                Map.entry("informationGaps", review.getInformationGaps()),
                Map.entry("clarificationQuestions", review.getClarificationQuestions()),
                Map.entry("riskAssessment", review.getRiskAssessment() == null ? Map.of() : review.getRiskAssessment()),
                Map.entry("recommendation", review.getReadinessRecommendation() == null
                        ? ""
                        : review.getReadinessRecommendation().name()),
                Map.entry("humanReviewStatus", review.getHumanReviewStatus().name()),
                Map.entry("preferredAiModel", nullSafe(review.getPreferredAiModel())),
                Map.entry("preferredAiProvider", nullSafe(review.getPreferredAiProvider())),
                Map.entry("packageName", nullSafe(review.getPackageName())),
                Map.entry("schemaOwner", nullSafe(review.getSchemaOwner())),
                Map.entry("createdAt", review.getCreatedAt().toString()),
                Map.entry("completedAt", review.getCompletedAt() == null ? "" : review.getCompletedAt().toString())
        );
    }

    private static String nullSafe(String v) {
        return v == null ? "" : v;
    }
}
