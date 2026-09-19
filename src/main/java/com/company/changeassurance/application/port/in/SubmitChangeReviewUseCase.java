package com.company.changeassurance.application.port.in;

import com.company.changeassurance.domain.model.ReviewId;
import com.company.changeassurance.domain.model.ReviewStage;
import com.company.changeassurance.domain.model.ReviewStatus;

/**
 * Inbound port for submitting a change package. Implementation arrives in Phase 3/5.
 */
public interface SubmitChangeReviewUseCase {

    SubmitChangeReviewResult submit(SubmitChangeReviewCommand command);

    record SubmitChangeReviewCommand(
            String applicationName,
            String changeTitle,
            String changeDescription,
            String changeType,
            String targetEnvironment,
            String implementationWindow,
            String deploymentPlan,
            String rollbackPlan,
            String testEvidence,
            String sqlOriginalFilename,
            byte[] sqlFileContent,
            String aiModel,
            String aiProvider,
            String packageName,
            String schemaOwner
    ) {
        public SubmitChangeReviewCommand(
                String applicationName,
                String changeTitle,
                String changeDescription,
                String changeType,
                String targetEnvironment,
                String implementationWindow,
                String deploymentPlan,
                String rollbackPlan,
                String testEvidence,
                String sqlOriginalFilename,
                byte[] sqlFileContent
        ) {
            this(
                    applicationName,
                    changeTitle,
                    changeDescription,
                    changeType,
                    targetEnvironment,
                    implementationWindow,
                    deploymentPlan,
                    rollbackPlan,
                    testEvidence,
                    sqlOriginalFilename,
                    sqlFileContent,
                    null,
                    null,
                    null,
                    null
            );
        }

        public SubmitChangeReviewCommand(
                String applicationName,
                String changeTitle,
                String changeDescription,
                String changeType,
                String targetEnvironment,
                String implementationWindow,
                String deploymentPlan,
                String rollbackPlan,
                String testEvidence,
                String sqlOriginalFilename,
                byte[] sqlFileContent,
                String aiModel
        ) {
            this(
                    applicationName,
                    changeTitle,
                    changeDescription,
                    changeType,
                    targetEnvironment,
                    implementationWindow,
                    deploymentPlan,
                    rollbackPlan,
                    testEvidence,
                    sqlOriginalFilename,
                    sqlFileContent,
                    aiModel,
                    null,
                    null,
                    null
            );
        }

        public SubmitChangeReviewCommand(
                String applicationName,
                String changeTitle,
                String changeDescription,
                String changeType,
                String targetEnvironment,
                String implementationWindow,
                String deploymentPlan,
                String rollbackPlan,
                String testEvidence,
                String sqlOriginalFilename,
                byte[] sqlFileContent,
                String aiModel,
                String packageName,
                String schemaOwner
        ) {
            this(
                    applicationName,
                    changeTitle,
                    changeDescription,
                    changeType,
                    targetEnvironment,
                    implementationWindow,
                    deploymentPlan,
                    rollbackPlan,
                    testEvidence,
                    sqlOriginalFilename,
                    sqlFileContent,
                    aiModel,
                    null,
                    packageName,
                    schemaOwner
            );
        }
    }

    record SubmitChangeReviewResult(
            ReviewId reviewId,
            ReviewStatus status,
            ReviewStage currentStage
    ) {
    }
}
