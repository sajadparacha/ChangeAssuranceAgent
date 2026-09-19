package com.company.changeassurance.application.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.company.changeassurance.application.port.in.GetChangeReviewUseCase;
import com.company.changeassurance.application.port.in.SubmitChangeReviewUseCase;
import com.company.changeassurance.application.port.in.SubmitClarificationAnswerUseCase;
import com.company.changeassurance.application.port.out.ActivityLogRepository;
import com.company.changeassurance.application.port.out.ChangeReviewRepository;
import com.company.changeassurance.application.port.out.ClockPort;
import com.company.changeassurance.application.port.out.FileStoragePort;
import com.company.changeassurance.application.port.out.ModelGateway;
import com.company.changeassurance.application.port.out.SqlAnalysisPort;
import com.company.changeassurance.application.service.AiConfigService;
import com.company.changeassurance.application.service.AiReasoningService;
import com.company.changeassurance.application.service.EvidenceCatalogService;
import com.company.changeassurance.application.service.IdGenerator;
import com.company.changeassurance.application.service.InvestigationLoopService;
import com.company.changeassurance.application.service.ReportAssembler;
import com.company.changeassurance.application.service.ToolRegistry;
import com.company.changeassurance.configuration.ChangeAssuranceProperties;
import com.company.changeassurance.domain.db.PackageImpactAssessment;
import com.company.changeassurance.domain.exception.AiUnavailableException;
import com.company.changeassurance.domain.exception.DomainValidationException;
import com.company.changeassurance.domain.model.ActivityId;
import com.company.changeassurance.domain.model.ActorType;
import com.company.changeassurance.domain.model.AffectedObject;
import com.company.changeassurance.domain.model.AiAssessment;
import com.company.changeassurance.domain.model.AiContributionEntry;
import com.company.changeassurance.domain.model.ChangeClassification;
import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.ChangeType;
import com.company.changeassurance.domain.model.ClarificationAnswer;
import com.company.changeassurance.domain.model.CriticResult;
import com.company.changeassurance.domain.model.Evidence;
import com.company.changeassurance.domain.model.EvidenceId;
import com.company.changeassurance.domain.model.EvidenceSource;
import com.company.changeassurance.domain.model.EvidenceType;
import com.company.changeassurance.domain.model.Finding;
import com.company.changeassurance.domain.model.FindingSeverity;
import com.company.changeassurance.domain.model.GapId;
import com.company.changeassurance.domain.model.InformationGap;
import com.company.changeassurance.domain.model.InformationGapResolutionStatus;
import com.company.changeassurance.domain.model.ReadinessRecommendation;
import com.company.changeassurance.domain.model.ReviewId;
import com.company.changeassurance.domain.model.ReviewPlan;
import com.company.changeassurance.domain.model.ReviewStage;
import com.company.changeassurance.domain.model.ReviewStatus;
import com.company.changeassurance.domain.model.RiskAssessment;
import com.company.changeassurance.domain.model.RiskScenario;
import com.company.changeassurance.domain.model.ToolActivity;
import com.company.changeassurance.domain.model.ToolActivityStatus;
import com.company.changeassurance.domain.model.ToolType;
import com.company.changeassurance.domain.policy.DefaultRecommendationPolicy;
import com.company.changeassurance.domain.policy.DeterministicRiskCalculator;
import com.company.changeassurance.domain.policy.PackageImpactAssessmentCalculator;
import com.company.changeassurance.domain.policy.RiskScoringConfig;
import com.company.changeassurance.domain.rule.AssuranceTool;
import com.company.changeassurance.domain.rule.ChangeReviewRule.ChangeReviewContext;
import com.company.changeassurance.domain.rule.ToolExecutionRequest;
import com.company.changeassurance.domain.rule.ToolExecutionResult;
import com.company.changeassurance.domain.sql.OraclePackageScriptAnalyzer;
import com.company.changeassurance.domain.sql.PackageDeployDelta;
import com.company.changeassurance.domain.sql.SqlOperationType;
import com.company.changeassurance.domain.sql.SqlParseResult;
import com.company.changeassurance.domain.sql.SqlStatementInfo;

@Service
public class ChangeAssuranceWorkflowService implements
        SubmitChangeReviewUseCase,
        GetChangeReviewUseCase,
        SubmitClarificationAnswerUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChangeAssuranceWorkflowService.class);

    private static final List<ToolType> DEFAULT_TOOLS = List.of(
            ToolType.CHECK_CHANGE_PACKAGE_COMPLETENESS,
            ToolType.ANALYZE_SQL_SCRIPT,
            ToolType.CHECK_CROSS_DOCUMENT_CONSISTENCY
    );

    private static final List<ToolType> PACKAGE_IMPACT_TOOLS =
            AiReasoningService.defaultPackageImpactTools();

    private final ChangeReviewRepository reviewRepository;
    private final ActivityLogRepository activityLogRepository;
    private final FileStoragePort fileStoragePort;
    private final SqlAnalysisPort sqlAnalysisPort;
    private final ToolRegistry toolRegistry;
    private final ModelGateway modelGateway;
    private final ClockPort clockPort;
    private final IdGenerator idGenerator;
    private final EvidenceCatalogService evidenceCatalogService;
    private final AiReasoningService aiReasoningService;
    private final AiConfigService aiConfigService;
    private final ReportAssembler reportAssembler;
    private final InvestigationLoopService investigationLoopService;
    private final DeterministicRiskCalculator riskCalculator;
    private final DefaultRecommendationPolicy recommendationPolicy;
    private final PackageImpactAssessmentCalculator impactAssessmentCalculator;

    public ChangeAssuranceWorkflowService(
            ChangeReviewRepository reviewRepository,
            ActivityLogRepository activityLogRepository,
            FileStoragePort fileStoragePort,
            SqlAnalysisPort sqlAnalysisPort,
            ToolRegistry toolRegistry,
            ModelGateway modelGateway,
            ClockPort clockPort,
            IdGenerator idGenerator,
            EvidenceCatalogService evidenceCatalogService,
            AiReasoningService aiReasoningService,
            AiConfigService aiConfigService,
            ReportAssembler reportAssembler,
            InvestigationLoopService investigationLoopService,
            ChangeAssuranceProperties properties
    ) {
        this.reviewRepository = reviewRepository;
        this.activityLogRepository = activityLogRepository;
        this.fileStoragePort = fileStoragePort;
        this.sqlAnalysisPort = sqlAnalysisPort;
        this.toolRegistry = toolRegistry;
        this.modelGateway = modelGateway;
        this.clockPort = clockPort;
        this.idGenerator = idGenerator;
        this.evidenceCatalogService = evidenceCatalogService;
        this.aiReasoningService = aiReasoningService;
        this.aiConfigService = aiConfigService;
        this.reportAssembler = reportAssembler;
        this.investigationLoopService = investigationLoopService;
        this.riskCalculator = new DeterministicRiskCalculator(RiskScoringConfig.defaults());
        this.recommendationPolicy = new DefaultRecommendationPolicy(RiskScoringConfig.defaults());
        this.impactAssessmentCalculator = new PackageImpactAssessmentCalculator(
                properties.investigation().largeDependentThreshold());
    }

    @Override
    public SubmitChangeReviewResult submit(SubmitChangeReviewCommand command) {
        validateCommand(command);
        Instant now = clockPort.now();
        ReviewId reviewId = new ReviewId(idGenerator.nextReviewId());

        String packageName = blankToNull(command.packageName());
        String schemaOwner = blankToNull(command.schemaOwner());
        String applicationName = blankToNull(command.applicationName());
        String changeTitle = blankToNull(command.changeTitle());
        if (packageName != null) {
            packageName = packageName.trim().toUpperCase(Locale.ROOT);
            if (schemaOwner != null) {
                schemaOwner = schemaOwner.trim().toUpperCase(Locale.ROOT);
            }
        }

        FileStoragePort.StoredFile stored = null;
        String sqlContent = null;
        if (command.sqlFileContent() != null && command.sqlFileContent().length > 0) {
            stored = fileStoragePort.store(
                    command.sqlOriginalFilename(),
                    "text/plain",
                    command.sqlFileContent().length,
                    new java.io.ByteArrayInputStream(command.sqlFileContent())
            );
            sqlContent = new String(command.sqlFileContent(), java.nio.charset.StandardCharsets.UTF_8);
        }

        SqlParseResult parseResult = sqlAnalysisPort.parseDetailed(
                sqlContent == null ? "" : sqlContent,
                command.sqlOriginalFilename()
        );
        PackageDeployDelta scriptDelta = OraclePackageScriptAnalyzer.analyze(parseResult);

        boolean packageInferredFromScript = false;
        if (packageName == null && scriptDelta.primaryPackageName() != null) {
            packageName = scriptDelta.primaryPackageName();
            packageInferredFromScript = true;
            if (schemaOwner == null) {
                schemaOwner = scriptDelta.primarySchemaOwner();
            }
        }

        if (packageName == null
                && (applicationName == null || changeTitle == null)
                && sqlContent != null) {
            throw new DomainValidationException(
                    "Could not derive packageName from SQL; provide packageName or both applicationName and changeTitle");
        }

        if (packageName != null) {
            if (applicationName == null) {
                applicationName = schemaOwner == null ? "database" : schemaOwner;
            }
            if (changeTitle == null) {
                changeTitle = packageInferredFromScript
                        ? "DB impact from script: " + packageName
                        : "DB impact: " + packageName;
            }
        }

        ChangeType submittedType = parseChangeType(command.changeType());
        if (packageName != null && submittedType == ChangeType.UNKNOWN
                && !notBlank(command.changeType())) {
            submittedType = ChangeType.PLSQL;
        }

        ChangeReview review = new ChangeReview(
                reviewId,
                applicationName,
                changeTitle,
                command.changeDescription(),
                command.targetEnvironment(),
                command.implementationWindow(),
                submittedType,
                now
        );
        review.setPackageTarget(packageName, schemaOwner);
        review.setPackageDeployDelta(scriptDelta.focusedOn(packageName));
        var aiSelection = aiConfigService.resolveSelection(command.aiProvider(), command.aiModel());
        review.setPreferredAiProvider(aiSelection.provider());
        review.setPreferredAiModel(aiSelection.model());

        transition(review, ReviewStage.VALIDATING_INPUT, "Validating submitted change package", ActorType.SYSTEM);

        review.setPackageDocuments(
                command.deploymentPlan(),
                command.rollbackPlan(),
                command.testEvidence(),
                sqlContent,
                command.sqlOriginalFilename(),
                stored == null ? null : stored.storageKey()
        );

        transition(review, ReviewStage.CREATING_EVIDENCE, "Creating initial evidence catalog", ActorType.SYSTEM);
        evidenceCatalogService.seedInitialEvidence(review, stored);
        populateAffectedObjects(review, parseResult);

        runClassificationAndPlanning(review, parseResult);
        executeApprovedTools(review, parseResult);
        analyzeGaps(review);

        if (shouldAskClarification(review)) {
            askClarification(review);
            reviewRepository.save(review);
            return new SubmitChangeReviewResult(reviewId, review.getReviewStatus(), review.getCurrentStage());
        }

        finalizeReview(review, parseResult);
        reviewRepository.save(review);
        return new SubmitChangeReviewResult(reviewId, review.getReviewStatus(), review.getCurrentStage());
    }

    @Override
    public void submit(ReviewId reviewId, SubmitClarificationAnswerCommand command) {
        ChangeReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new DomainValidationException("Review not found: " + reviewId));
        if (review.getCurrentStage() != ReviewStage.WAITING_FOR_INFORMATION) {
            throw new DomainValidationException("Review is not waiting for clarification");
        }
        if (command.answer() == null || command.answer().isBlank()) {
            throw new DomainValidationException("Answer must not be blank");
        }

        GapId gapId = new GapId(command.gapId());
        InformationGap matched = review.getInformationGaps().stream()
                .filter(g -> g.gapId().equals(gapId))
                .findFirst()
                .orElseThrow(() -> new DomainValidationException("Unknown gap: " + command.gapId()));

        EvidenceId answerEvidenceId = new EvidenceId(idGenerator.nextEvidenceId());
        Evidence answerEvidence = new Evidence(
                answerEvidenceId,
                EvidenceType.USER_ANSWER,
                EvidenceSource.USER_CLARIFICATION,
                command.gapId(),
                "Clarification answer",
                command.answer(),
                null,
                null,
                clockPort.now()
        );
        review.addEvidence(answerEvidence);
        review.addUserAnswer(new ClarificationAnswer(
                gapId,
                matched.suggestedQuestion() == null ? matched.description() : matched.suggestedQuestion(),
                command.answer(),
                clockPort.now(),
                answerEvidenceId
        ));

        // Replace gap with answered status
        List<InformationGap> updated = new ArrayList<>();
        for (InformationGap gap : review.getInformationGaps()) {
            if (gap.gapId().equals(gapId)) {
                updated.add(new InformationGap(
                        gap.gapId(),
                        gap.description(),
                        gap.reasonRequired(),
                        gap.severity(),
                        gap.relatedEvidenceIds(),
                        gap.suggestedQuestion(),
                        InformationGapResolutionStatus.ANSWERED,
                        command.answer()
                ));
            } else {
                updated.add(gap);
            }
        }
        // Clear and re-add — ChangeReview doesn't have replace; mutate via reflection-free approach:
        // add only new answered representation by filtering through a dedicated method
        review.replaceInformationGaps(updated);

        SqlParseResult parseResult = sqlAnalysisPort.parseDetailed(
                review.getSqlContent() == null ? "" : review.getSqlContent(),
                review.getSqlFilename()
        );
        transition(review, ReviewStage.ANALYZING_EVIDENCE, "Resuming after clarification", ActorType.HUMAN);
        executeApprovedTools(review, parseResult);
        finalizeReview(review, parseResult);
        reviewRepository.save(review);
    }

    @Override
    public Optional<ChangeReview> getById(ReviewId reviewId) {
        return reviewRepository.findById(reviewId);
    }

    @Override
    public List<ChangeReview> listReviews() {
        return reviewRepository.findAll();
    }

    public Object getReport(ReviewId reviewId) {
        ChangeReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new DomainValidationException("Review not found: " + reviewId));
        return reportAssembler.assemble(review);
    }

    private void runClassificationAndPlanning(ChangeReview review, SqlParseResult parseResult) {
        transition(review, ReviewStage.CLASSIFYING_CHANGE, "Classifying change", ActorType.AI);
        ChangeClassification classification = aiReasoningService.classify(review, parseResult);
        review.setChangeClassification(classification);

        transition(review, ReviewStage.PLANNING_REVIEW, "Generating review plan", ActorType.AI);
        ReviewPlan plan = aiReasoningService.planReview(review, classification);
        validatePlan(plan);
        review.setReviewPlan(plan);
    }

    private void validatePlan(ReviewPlan plan) {
        for (ToolType toolType : plan.requiredTools()) {
            if (!toolRegistry.isApproved(toolType)) {
                throw new DomainValidationException("Plan references unapproved tool: " + toolType);
            }
        }
    }

    private void executeApprovedTools(ChangeReview review, SqlParseResult parseResult) {
        transition(review, ReviewStage.EXECUTING_TOOLS, "Executing deterministic assurance tools", ActorType.SYSTEM);
        ChangeReviewContext context = buildContext(review, parseResult);
        List<ToolType> tools = review.getReviewPlan() == null
                ? (isPackageImpactOnly(review) ? PACKAGE_IMPACT_TOOLS : DEFAULT_TOOLS)
                : review.getReviewPlan().requiredTools();

        java.util.LinkedHashSet<ToolType> alreadyRun = new java.util.LinkedHashSet<>();
        for (ToolType toolType : tools) {
            if (toolType == ToolType.CALCULATE_DETERMINISTIC_RISK
                    || toolType == ToolType.VALIDATE_EVIDENCE_REFERENCES) {
                continue;
            }
            runTool(review, toolType, context);
            alreadyRun.add(toolType);
            context = buildContext(review, parseResult);
        }

        if (review.isPackageImpactMode()) {
            int maxRounds = investigationLoopService.maxFollowUpRounds();
            for (int round = 1; round <= maxRounds; round++) {
                transition(review, ReviewStage.ANALYZING_EVIDENCE,
                        "Examining evidence for follow-up investigation round " + round,
                        ActorType.SYSTEM);
                List<ToolType> followUps = investigationLoopService.suggestFollowUpTools(review, alreadyRun);
                if (followUps.isEmpty()) {
                    break;
                }
                for (ToolType followUp : followUps) {
                    if (!toolRegistry.isApproved(followUp) || alreadyRun.contains(followUp)) {
                        continue;
                    }
                    String reason = investigationLoopService.reasonFor(followUp, review);
                    transition(review, ReviewStage.EXECUTING_TOOLS, reason, ActorType.SYSTEM);
                    log.info("Investigation follow-up round {}: {}", round, reason);
                    runTool(review, followUp, buildContext(review, parseResult));
                    alreadyRun.add(followUp);
                }
            }
        }

        transition(review, ReviewStage.ANALYZING_EVIDENCE, "Analyzing tool evidence", ActorType.SYSTEM);
    }

    private void runTool(ChangeReview review, ToolType toolType, ChangeReviewContext context) {
        AssuranceTool tool = toolRegistry.require(toolType);
        Instant start = clockPort.now();
        ActivityId activityId = new ActivityId(idGenerator.nextActivityId());
        String reason = "Stage tool execution for " + toolType;
        try {
            ToolExecutionResult result = tool.execute(new ToolExecutionRequest(
                    review.getReviewId().value(),
                    toolType,
                    context.evidenceCatalog().stream().map(Evidence::evidenceId).toList(),
                    reason,
                    context
            ));
            for (Evidence e : result.outputEvidence()) {
                review.addEvidence(e);
            }
            for (Finding f : result.findings()) {
                review.addFinding(f);
            }
            ToolActivity activity = new ToolActivity(
                    activityId,
                    review.getReviewId(),
                    toolType,
                    start,
                    clockPort.now(),
                    result.status(),
                    result.safeSummary(),
                    context.evidenceCatalog().stream().map(Evidence::evidenceId).toList(),
                    result.outputEvidence().stream().map(Evidence::evidenceId).toList(),
                    result.errorCode(),
                    result.errorDescription()
            );
            activityLogRepository.saveToolActivity(activity);
            log.info("Stage: {} Tool: {} Reason: {} Result: {} Evidence: {}",
                    review.getCurrentStage(), toolType, reason, result.safeSummary(),
                    result.outputEvidence().stream().map(e -> e.evidenceId().value()).toList());
        } catch (Exception ex) {
            log.warn("Tool {} failed safely: {}", toolType, ex.getMessage());
            activityLogRepository.saveToolActivity(new ToolActivity(
                    activityId,
                    review.getReviewId(),
                    toolType,
                    start,
                    clockPort.now(),
                    ToolActivityStatus.FAILED,
                    "Tool failed safely",
                    List.of(),
                    List.of(),
                    "TOOL_FAILURE",
                    ex.getMessage()
            ));
        }
    }

    private void analyzeGaps(ChangeReview review) {
        List<InformationGap> gaps = new ArrayList<>();
        boolean missingRollbackObject = review.getFindings().stream()
                .anyMatch(f -> "RBK-001".equals(f.ruleCode()));
        boolean understated = review.getFindings().stream()
                .anyMatch(f -> "CON-003".equals(f.ruleCode()));
        if (missingRollbackObject || understated) {
            List<EvidenceId> related = review.getFindings().stream()
                    .filter(f -> "RBK-001".equals(f.ruleCode()) || "CON-003".equals(f.ruleCode()))
                    .flatMap(f -> f.evidenceIds().stream())
                    .distinct()
                    .toList();
            String description = missingRollbackObject
                    ? "Confirm whether schema/package-spec/table changes were intentionally omitted from plans."
                    : "Confirm whether the change description understates the SQL scope.";
            String reason = missingRollbackObject
                    ? "Rollback and scope contradictions prevent a safe readiness assessment."
                    : "Scope contradictions prevent a safe readiness assessment.";
            String question = missingRollbackObject
                    ? "Were the table/package-specification changes intentional, and what is the complete rollback for each affected object?"
                    : "Were all table/schema/package changes intentional, and does the description reflect full SQL scope?";
            gaps.add(new InformationGap(
                    new GapId(idGenerator.nextGapId()),
                    description,
                    reason,
                    FindingSeverity.HIGH,
                    related,
                    question,
                    InformationGapResolutionStatus.OPEN,
                    null
            ));
        }
        for (InformationGap gap : gaps) {
            review.addInformationGap(gap);
        }
    }

    private boolean shouldAskClarification(ChangeReview review) {
        // Clarification round disabled: complete the review with open gaps recorded
        // on the report instead of blocking on WAITING_FOR_INFORMATION.
        return false;
    }

    private void askClarification(ChangeReview review) {
        transition(review, ReviewStage.WAITING_FOR_INFORMATION, "Awaiting one clarification round", ActorType.AI);
        try {
            List<String> questions = aiReasoningService.gapQuestions(review);
            for (String q : questions) {
                review.addClarificationQuestion(q);
            }
        } catch (AiUnavailableException ex) {
            review.getInformationGaps().stream().findFirst().ifPresent(g ->
                    review.addClarificationQuestion(g.suggestedQuestion()));
        }
    }

    private void finalizeReview(ChangeReview review, SqlParseResult parseResult) {
        transition(review, ReviewStage.GENERATING_RISK_SCENARIOS, "Generating operational risk scenarios", ActorType.AI);
        try {
            List<RiskScenario> scenarios = aiReasoningService.riskScenarios(review);
            for (RiskScenario scenario : scenarios) {
                review.addRiskScenario(scenario);
            }
        } catch (AiUnavailableException ex) {
            log.info("Risk scenario generation unavailable");
        }

        transition(review, ReviewStage.GENERATING_DRAFT_REPORT, "Generating draft assurance report", ActorType.AI);
        AiAssessment assessment;
        try {
            assessment = aiReasoningService.draftReport(review);
        } catch (AiUnavailableException ex) {
            assessment = AiAssessment.unavailable();
        }
        review.setAiAssessment(assessment);

        transition(review, ReviewStage.CRITIC_REVIEW, "Running critic review", ActorType.AI);
        CriticResult critic;
        try {
            critic = aiReasoningService.critic(review);
        } catch (AiUnavailableException ex) {
            critic = CriticResult.unavailable();
        }
        review.setCriticResult(critic);

        // Evidence validation tool
        runTool(review, ToolType.VALIDATE_EVIDENCE_REFERENCES, buildContext(review, parseResult));

        if (review.getPackageDbImpact() != null && review.getPackageDbImpact().packageFound()) {
            PackageImpactAssessment impactAssessment =
                    impactAssessmentCalculator.assess(
                            review.getPackageDbImpact(),
                            review.getPackageDeployDelta());
            review.setPackageDbImpact(review.getPackageDbImpact().withImpactAssessment(impactAssessment));
        }

        transition(review, ReviewStage.CALCULATING_RECOMMENDATION, "Calculating deterministic risk and recommendation", ActorType.SYSTEM);
        double coverage = evidenceCoverage(review);
        boolean rollbackGaps = review.getFindings().stream().anyMatch(f -> f.ruleCode().startsWith("RBK-001"));
        boolean unsupportedSql = parseResult != null && !parseResult.fullySupported();
        RiskAssessment risk = riskCalculator.calculate(
                review.getFindings(),
                review.getInformationGaps(),
                coverage,
                rollbackGaps,
                unsupportedSql
        );
        review.setRiskAssessment(risk);

        ReadinessRecommendation recommendation = recommendationPolicy.decide(
                risk,
                review.getFindings(),
                review.getInformationGaps(),
                coverage
        );
        review.setReadinessRecommendation(recommendation);
        review.setDeterministicReasonCodes(buildReasonCodes(review, recommendation));

        review.addAiContribution(new AiContributionEntry(
                "Risk score and readiness recommendation",
                "Compute numerical risk and GO / CONDITIONAL_GO / NO_GO recommendation",
                AiContributionEntry.DETERMINISTIC_RULES,
                "Policy engine set " + recommendation.name()
                        + "; AI is not allowed to override this decision"
        ));
        review.addAiContribution(new AiContributionEntry(
                "SQL safety and assurance tools",
                "Parse uploaded SQL and run approved deterministic tools",
                AiContributionEntry.DETERMINISTIC_RULES,
                review.getFindings().size() + " finding(s) produced without executing change SQL"
        ));

        // Ensure AI cannot override — already enforced by assigning only from policy
        transition(review, ReviewStage.COMPLETED, "Review completed for human approval", ActorType.SYSTEM);
    }

    private List<String> buildReasonCodes(ChangeReview review, ReadinessRecommendation recommendation) {
        List<String> codes = new ArrayList<>();
        codes.add("REC-" + recommendation.name());
        if (review.getPackageDbImpact() != null && review.getPackageDbImpact().impactAssessment() != null) {
            codes.add("IMPACT-" + review.getPackageDbImpact().impactAssessment().overallImpact().name());
        }
        review.getFindings().stream()
                .filter(f -> f.severity() == FindingSeverity.CRITICAL || f.severity() == FindingSeverity.HIGH)
                .map(Finding::ruleCode)
                .distinct()
                .forEach(codes::add);
        return codes;
    }

    private double evidenceCoverage(ChangeReview review) {
        if (isPackageImpactOnly(review)) {
            if (review.getPackageDbImpact() == null || !review.getPackageDbImpact().packageFound()) {
                return 0.2d;
            }
            double coverage = 0.55d;
            var impact = review.getPackageDbImpact();
            if (!impact.dependents().isEmpty() || !impact.dependencies().isEmpty()) {
                coverage += 0.1d;
            }
            if (!impact.schedulerJobs().isEmpty() || !impact.transitiveDependents().isEmpty()) {
                coverage += 0.1d;
            }
            if (impact.impactAssessment() != null) {
                coverage += 0.05d;
            }
            return Math.min(coverage, 0.9d);
        }
        int required = 2;
        int present = 0;
        if (notBlank(review.getChangeDescription())) present++;
        if (notBlank(review.getSqlContent())) present++;
        // Deployment / rollback / test evidence are optional for current UI-driven reviews.
        if (notBlank(review.getDeploymentPlan())) {
            required++;
            present++;
        }
        if (notBlank(review.getRollbackPlan())) {
            required++;
            present++;
        }
        if (notBlank(review.getTestEvidence())) {
            required++;
            present++;
        }
        return (double) present / (double) required;
    }

    private ChangeReviewContext buildContext(ChangeReview review, SqlParseResult parseResult) {
        return new ChangeReviewContext(
                review,
                review.getDeploymentPlan(),
                review.getRollbackPlan(),
                review.getTestEvidence(),
                review.getSqlContent(),
                review.getSqlFilename(),
                parseResult,
                review.getEvidence(),
                review.getAffectedObjects()
        );
    }

    private void populateAffectedObjects(ChangeReview review, SqlParseResult parseResult) {
        for (SqlStatementInfo stmt : parseResult.statements()) {
            String objectType = mapObjectType(stmt.operationType());
            List<String> schemas = stmt.objectSchemas();
            for (int i = 0; i < stmt.objectNames().size(); i++) {
                String name = stmt.objectNames().get(i);
                String schema = i < schemas.size() ? schemas.get(i) : null;
                review.addAffectedObject(new AffectedObject(
                        name,
                        objectType,
                        schema,
                        stmt.operationType().name(),
                        null
                ));
            }
        }
    }

    private static String mapObjectType(SqlOperationType op) {
        return switch (op) {
            case PACKAGE_SPEC -> "PACKAGE_SPEC";
            case PACKAGE_BODY -> "PACKAGE_BODY";
            case ALTER, CREATE, DROP, TRUNCATE -> "TABLE";
            default -> "OBJECT";
        };
    }

    private void transition(ChangeReview review, ReviewStage stage, String reason, ActorType actor) {
        review.transitionTo(stage, reason, actor, clockPort.now(), List.of(), List.of());
        if (!review.getStageTransitions().isEmpty()) {
            activityLogRepository.saveStageTransition(
                    review.getStageTransitions().get(review.getStageTransitions().size() - 1)
            );
        }
    }

    private void validateCommand(SubmitChangeReviewCommand command) {
        boolean hasPackage = notBlank(command.packageName());
        boolean hasApp = notBlank(command.applicationName());
        boolean hasTitle = notBlank(command.changeTitle());
        boolean hasSql = command.sqlFileContent() != null && command.sqlFileContent().length > 0;
        if (!hasPackage && (!hasApp || !hasTitle) && !hasSql) {
            throw new DomainValidationException(
                    "packageName is required, or both applicationName and changeTitle, or an SQL file with package DDL");
        }
        if (hasSql && (command.sqlOriginalFilename() == null || command.sqlOriginalFilename().isBlank())) {
            throw new DomainValidationException("sqlFile original filename is required");
        }
    }

    private static boolean isPackageImpactOnly(ChangeReview review) {
        return review.isPackageImpactMode() && !review.hasSubmittedChangePackageDocs();
    }

    private static ChangeType parseChangeType(String value) {
        if (value == null || value.isBlank()) {
            return ChangeType.UNKNOWN;
        }
        try {
            return ChangeType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ChangeType.UNKNOWN;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
