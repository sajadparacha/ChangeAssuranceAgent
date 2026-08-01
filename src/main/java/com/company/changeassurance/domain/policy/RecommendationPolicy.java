package com.company.changeassurance.domain.policy;

import java.util.List;

import com.company.changeassurance.domain.model.Finding;
import com.company.changeassurance.domain.model.InformationGap;
import com.company.changeassurance.domain.model.ReadinessRecommendation;
import com.company.changeassurance.domain.model.RiskAssessment;

/**
 * Deterministic recommendation policy. AI must never alter this result.
 * Implementation arrives in Phase 2.
 */
public interface RecommendationPolicy {

    ReadinessRecommendation decide(
            RiskAssessment riskAssessment,
            List<Finding> findings,
            List<InformationGap> informationGaps,
            double evidenceCoverageScore
    );
}
