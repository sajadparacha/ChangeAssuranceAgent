package com.company.changeassurance.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.company.changeassurance.domain.db.PackageDbImpact;
import com.company.changeassurance.domain.sql.PackageDeployDelta;

/**
 * Aggregate root for a change assurance review.
 *
 * <p>Framework-free mutable aggregate used by application services. Persistence mapping
 * belongs in outbound adapters (later phases).
 */
public final class ChangeReview {

    private final ReviewId reviewId;
    private String applicationName;
    private String changeTitle;
    private String changeDescription;
    private String targetEnvironment;
    private String implementationWindow;
    private String packageName;
    private String schemaOwner;
    private PackageDbImpact packageDbImpact;
    private PackageDeployDelta packageDeployDelta;
    private String deploymentPlan;
    private String rollbackPlan;
    private String testEvidence;
    private String sqlContent;
    private String sqlFilename;
    private String sqlStorageKey;
    private String preferredAiModel;
    private String preferredAiProvider;
    private List<String> deterministicReasonCodes;
    private ChangeType submittedChangeType;
    private ReviewStatus reviewStatus;
    private ReviewStage currentStage;
    private ChangeClassification changeClassification;
    private ReviewPlan reviewPlan;
    private final List<AffectedObject> affectedObjects;
    private final List<Evidence> evidence;
    private final List<Finding> findings;
    private final List<InformationGap> informationGaps;
    private final List<String> clarificationQuestions;
    private final List<ClarificationAnswer> userAnswers;
    private final List<RiskScenario> riskScenarios;
    private final List<AiContributionEntry> aiContributions;
    private RiskAssessment riskAssessment;
    private ReadinessRecommendation readinessRecommendation;
    private AiAssessment aiAssessment;
    private CriticResult criticResult;
    private HumanReviewStatus humanReviewStatus;
    private final Instant createdAt;
    private Instant completedAt;
    private final List<StageTransition> stageTransitions;

    public ChangeReview(
            ReviewId reviewId,
            String applicationName,
            String changeTitle,
            String changeDescription,
            String targetEnvironment,
            String implementationWindow,
            ChangeType submittedChangeType,
            Instant createdAt
    ) {
        this.reviewId = Objects.requireNonNull(reviewId, "reviewId must not be null");
        this.applicationName = requireNonBlank(applicationName, "applicationName");
        this.changeTitle = requireNonBlank(changeTitle, "changeTitle");
        this.changeDescription = changeDescription;
        this.targetEnvironment = targetEnvironment;
        this.implementationWindow = implementationWindow;
        this.submittedChangeType = submittedChangeType;
        this.reviewStatus = ReviewStatus.RECEIVED;
        this.currentStage = ReviewStage.RECEIVED;
        this.affectedObjects = new ArrayList<>();
        this.evidence = new ArrayList<>();
        this.findings = new ArrayList<>();
        this.informationGaps = new ArrayList<>();
        this.clarificationQuestions = new ArrayList<>();
        this.userAnswers = new ArrayList<>();
        this.riskScenarios = new ArrayList<>();
        this.aiContributions = new ArrayList<>();
        this.humanReviewStatus = HumanReviewStatus.PENDING;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.stageTransitions = new ArrayList<>();
        this.deterministicReasonCodes = new ArrayList<>();
    }

    public void setPackageDocuments(
            String deploymentPlan,
            String rollbackPlan,
            String testEvidence,
            String sqlContent,
            String sqlFilename,
            String sqlStorageKey
    ) {
        this.deploymentPlan = deploymentPlan;
        this.rollbackPlan = rollbackPlan;
        this.testEvidence = testEvidence;
        this.sqlContent = sqlContent;
        this.sqlFilename = sqlFilename;
        this.sqlStorageKey = sqlStorageKey;
    }

    public void setPackageTarget(String packageName, String schemaOwner) {
        this.packageName = blankToNull(packageName);
        this.schemaOwner = blankToNull(schemaOwner);
        if (this.packageName != null) {
            this.packageName = this.packageName.trim().toUpperCase();
        }
        if (this.schemaOwner != null) {
            this.schemaOwner = this.schemaOwner.trim().toUpperCase();
        }
    }

    public void setPackageDeployDelta(PackageDeployDelta packageDeployDelta) {
        this.packageDeployDelta = packageDeployDelta == null
                ? PackageDeployDelta.none()
                : packageDeployDelta;
    }

    public boolean isPackageImpactMode() {
        return packageName != null && !packageName.isBlank();
    }

    public boolean hasSubmittedChangePackageDocs() {
        return notBlank(changeDescription)
                || notBlank(deploymentPlan)
                || notBlank(rollbackPlan)
                || notBlank(testEvidence)
                || notBlank(sqlContent);
    }

    public void transitionTo(
            ReviewStage newStage,
            String reason,
            ActorType actorType,
            Instant timestamp,
            List<ActivityId> relatedToolExecutionIds,
            List<EvidenceId> relatedEvidenceIds
    ) {
        Objects.requireNonNull(newStage, "newStage must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");

        ReviewStage previous = this.currentStage;
        this.currentStage = newStage;
        this.stageTransitions.add(new StageTransition(
                reviewId,
                previous,
                newStage,
                timestamp,
                reason,
                actorType,
                relatedToolExecutionIds == null ? List.of() : relatedToolExecutionIds,
                relatedEvidenceIds == null ? List.of() : relatedEvidenceIds
        ));

        if (newStage == ReviewStage.WAITING_FOR_INFORMATION) {
            this.reviewStatus = ReviewStatus.WAITING_FOR_INFORMATION;
        } else if (newStage == ReviewStage.COMPLETED) {
            this.reviewStatus = ReviewStatus.COMPLETED;
            this.completedAt = timestamp;
        } else if (newStage == ReviewStage.FAILED) {
            this.reviewStatus = ReviewStatus.FAILED;
            this.completedAt = timestamp;
        } else if (this.reviewStatus == ReviewStatus.RECEIVED
                || this.reviewStatus == ReviewStatus.WAITING_FOR_INFORMATION) {
            this.reviewStatus = ReviewStatus.IN_PROGRESS;
        }
    }

    public ReviewId getReviewId() {
        return reviewId;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getChangeTitle() {
        return changeTitle;
    }

    public String getChangeDescription() {
        return changeDescription;
    }

    public String getTargetEnvironment() {
        return targetEnvironment;
    }

    public String getImplementationWindow() {
        return implementationWindow;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getSchemaOwner() {
        return schemaOwner;
    }

    public PackageDbImpact getPackageDbImpact() {
        return packageDbImpact;
    }

    public void setPackageDbImpact(PackageDbImpact packageDbImpact) {
        this.packageDbImpact = packageDbImpact;
    }

    public PackageDeployDelta getPackageDeployDelta() {
        return packageDeployDelta == null ? PackageDeployDelta.none() : packageDeployDelta;
    }

    public String getDeploymentPlan() {
        return deploymentPlan;
    }

    public String getRollbackPlan() {
        return rollbackPlan;
    }

    public String getTestEvidence() {
        return testEvidence;
    }

    public String getSqlContent() {
        return sqlContent;
    }

    public String getSqlFilename() {
        return sqlFilename;
    }

    public String getSqlStorageKey() {
        return sqlStorageKey;
    }

    public String getPreferredAiModel() {
        return preferredAiModel;
    }

    public void setPreferredAiModel(String preferredAiModel) {
        if (preferredAiModel == null || preferredAiModel.isBlank()) {
            this.preferredAiModel = null;
        } else {
            this.preferredAiModel = preferredAiModel.trim();
        }
    }

    public String getPreferredAiProvider() {
        return preferredAiProvider;
    }

    public void setPreferredAiProvider(String preferredAiProvider) {
        if (preferredAiProvider == null || preferredAiProvider.isBlank()) {
            this.preferredAiProvider = null;
        } else {
            this.preferredAiProvider = preferredAiProvider.trim();
        }
    }

    public List<String> getDeterministicReasonCodes() {
        return List.copyOf(deterministicReasonCodes);
    }

    public void setDeterministicReasonCodes(List<String> codes) {
        this.deterministicReasonCodes = new ArrayList<>(codes == null ? List.of() : codes);
    }

    public ChangeType getSubmittedChangeType() {
        return submittedChangeType;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public ReviewStage getCurrentStage() {
        return currentStage;
    }

    public ChangeClassification getChangeClassification() {
        return changeClassification;
    }

    public void setChangeClassification(ChangeClassification changeClassification) {
        this.changeClassification = changeClassification;
    }

    public ReviewPlan getReviewPlan() {
        return reviewPlan;
    }

    public void setReviewPlan(ReviewPlan reviewPlan) {
        this.reviewPlan = reviewPlan;
    }

    public List<AffectedObject> getAffectedObjects() {
        return List.copyOf(affectedObjects);
    }

    public void addAffectedObject(AffectedObject affectedObject) {
        affectedObjects.add(Objects.requireNonNull(affectedObject));
    }

    public List<Evidence> getEvidence() {
        return List.copyOf(evidence);
    }

    public void addEvidence(Evidence item) {
        evidence.add(Objects.requireNonNull(item));
    }

    public List<Finding> getFindings() {
        return List.copyOf(findings);
    }

    public void addFinding(Finding finding) {
        findings.add(Objects.requireNonNull(finding));
    }

    public List<InformationGap> getInformationGaps() {
        return List.copyOf(informationGaps);
    }

    public void addInformationGap(InformationGap gap) {
        informationGaps.add(Objects.requireNonNull(gap));
    }

    public void replaceInformationGaps(List<InformationGap> gaps) {
        informationGaps.clear();
        if (gaps != null) {
            informationGaps.addAll(gaps);
        }
    }

    public List<String> getClarificationQuestions() {
        return List.copyOf(clarificationQuestions);
    }

    public void addClarificationQuestion(String question) {
        clarificationQuestions.add(Objects.requireNonNull(question));
    }

    public List<ClarificationAnswer> getUserAnswers() {
        return List.copyOf(userAnswers);
    }

    public void addUserAnswer(ClarificationAnswer answer) {
        userAnswers.add(Objects.requireNonNull(answer));
    }

    public List<RiskScenario> getRiskScenarios() {
        return List.copyOf(riskScenarios);
    }

    public void addRiskScenario(RiskScenario scenario) {
        riskScenarios.add(Objects.requireNonNull(scenario));
    }

    public List<AiContributionEntry> getAiContributions() {
        return List.copyOf(aiContributions);
    }

    public void addAiContribution(AiContributionEntry entry) {
        aiContributions.add(Objects.requireNonNull(entry));
    }

    public RiskAssessment getRiskAssessment() {
        return riskAssessment;
    }

    public void setRiskAssessment(RiskAssessment riskAssessment) {
        this.riskAssessment = riskAssessment;
    }

    public ReadinessRecommendation getReadinessRecommendation() {
        return readinessRecommendation;
    }

    public void setReadinessRecommendation(ReadinessRecommendation readinessRecommendation) {
        this.readinessRecommendation = readinessRecommendation;
    }

    public AiAssessment getAiAssessment() {
        return aiAssessment;
    }

    public void setAiAssessment(AiAssessment aiAssessment) {
        this.aiAssessment = aiAssessment;
    }

    public CriticResult getCriticResult() {
        return criticResult;
    }

    public void setCriticResult(CriticResult criticResult) {
        this.criticResult = criticResult;
    }

    public HumanReviewStatus getHumanReviewStatus() {
        return humanReviewStatus;
    }

    public void setHumanReviewStatus(HumanReviewStatus humanReviewStatus) {
        this.humanReviewStatus = Objects.requireNonNull(humanReviewStatus);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<StageTransition> getStageTransitions() {
        return List.copyOf(stageTransitions);
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
