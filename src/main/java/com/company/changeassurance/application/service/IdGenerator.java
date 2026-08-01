package com.company.changeassurance.application.service;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class IdGenerator {

    private final AtomicInteger reviewSeq = new AtomicInteger(0);
    private final AtomicInteger evidenceSeq = new AtomicInteger(0);
    private final AtomicInteger findingSeq = new AtomicInteger(0);
    private final AtomicInteger gapSeq = new AtomicInteger(0);
    private final AtomicInteger activitySeq = new AtomicInteger(0);
    private final AtomicInteger planSeq = new AtomicInteger(0);
    private final AtomicInteger scenarioSeq = new AtomicInteger(0);

    public String nextReviewId() {
        return "REV-" + LocalDate.now().getYear() + "-" + String.format("%05d", reviewSeq.incrementAndGet());
    }

    public String nextEvidenceId() {
        return "EV-" + String.format("%03d", evidenceSeq.incrementAndGet());
    }

    public String nextFindingId() {
        return "FND-" + String.format("%03d", findingSeq.incrementAndGet());
    }

    public String nextGapId() {
        return "GAP-" + String.format("%03d", gapSeq.incrementAndGet());
    }

    public String nextActivityId() {
        return "ACT-" + String.format("%03d", activitySeq.incrementAndGet());
    }

    public String nextPlanId() {
        return "PLAN-" + String.format("%03d", planSeq.incrementAndGet());
    }

    public String nextScenarioId() {
        return "RS-" + String.format("%03d", scenarioSeq.incrementAndGet());
    }

    public String nextStepId() {
        return "STEP-" + String.format("%03d", evidenceSeq.incrementAndGet());
    }
}
