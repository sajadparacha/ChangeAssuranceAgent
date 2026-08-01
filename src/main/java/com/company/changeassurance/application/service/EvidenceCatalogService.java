package com.company.changeassurance.application.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.company.changeassurance.application.port.out.ClockPort;
import com.company.changeassurance.application.port.out.FileStoragePort;
import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.Evidence;
import com.company.changeassurance.domain.model.EvidenceId;
import com.company.changeassurance.domain.model.EvidenceSource;
import com.company.changeassurance.domain.model.EvidenceType;

@Service
public class EvidenceCatalogService {

    private final IdGenerator idGenerator;
    private final ClockPort clockPort;

    public EvidenceCatalogService(IdGenerator idGenerator, ClockPort clockPort) {
        this.idGenerator = idGenerator;
        this.clockPort = clockPort;
    }

    public void seedInitialEvidence(ChangeReview review, FileStoragePort.StoredFile storedFile) {
        Instant now = clockPort.now();
        add(review, EvidenceType.CHANGE_DESCRIPTION, EvidenceSource.SUBMITTED_PACKAGE,
                "changeDescription", "Submitted change description", review.getChangeDescription(), now);
        add(review, EvidenceType.DEPLOYMENT_PLAN, EvidenceSource.SUBMITTED_PACKAGE,
                "deploymentPlan", "Submitted deployment plan", review.getDeploymentPlan(), now);
        add(review, EvidenceType.ROLLBACK_PLAN, EvidenceSource.SUBMITTED_PACKAGE,
                "rollbackPlan", "Submitted rollback plan", review.getRollbackPlan(), now);
        add(review, EvidenceType.TEST_EVIDENCE, EvidenceSource.SUBMITTED_PACKAGE,
                "testEvidence", "Submitted test evidence", review.getTestEvidence(), now);
        if (storedFile != null) {
            review.addEvidence(new Evidence(
                    new EvidenceId(idGenerator.nextEvidenceId()),
                    EvidenceType.SQL_FILE,
                    EvidenceSource.UPLOADED_FILE,
                    storedFile.originalFilename(),
                    "Uploaded SQL file",
                    "Stored as " + storedFile.serverFilename() + " hash=" + storedFile.contentHash(),
                    null,
                    storedFile.contentHash(),
                    now
            ));
        }
    }

    private void add(
            ChangeReview review,
            EvidenceType type,
            EvidenceSource source,
            String ref,
            String description,
            String content,
            Instant now
    ) {
        if (content == null || content.isBlank()) {
            return;
        }
        String excerpt = content.length() > 500 ? content.substring(0, 500) + "..." : content;
        review.addEvidence(new Evidence(
                new EvidenceId(idGenerator.nextEvidenceId()),
                type,
                source,
                ref,
                description,
                excerpt,
                null,
                null,
                now
        ));
    }
}
