package com.company.changeassurance.application.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.company.changeassurance.configuration.ChangeAssuranceProperties;
import com.company.changeassurance.domain.db.DbObjectRef;
import com.company.changeassurance.domain.db.PackageDbImpact;
import com.company.changeassurance.domain.model.ChangeReview;
import com.company.changeassurance.domain.model.ToolType;

/**
 * Bounded follow-up tool selection for package impact investigations.
 * Never invents tools outside {@link ToolType}; AI may only choose from approved follow-ups.
 */
@Service
public class InvestigationLoopService {

    private final ChangeAssuranceProperties properties;

    public InvestigationLoopService(ChangeAssuranceProperties properties) {
        this.properties = properties;
    }

    public int maxFollowUpRounds() {
        return properties.investigation().maxFollowUpRounds();
    }

    /**
     * Deterministic heuristics for additional approved tools after an investigation round.
     */
    public List<ToolType> suggestFollowUpTools(ChangeReview review, Set<ToolType> alreadyRun) {
        Set<ToolType> already = alreadyRun == null ? EnumSet.noneOf(ToolType.class) : EnumSet.copyOf(alreadyRun);
        LinkedHashSet<ToolType> suggested = new LinkedHashSet<>();
        if (!review.isPackageImpactMode()) {
            return List.of();
        }
        PackageDbImpact impact = review.getPackageDbImpact();
        if (impact == null || !impact.packageFound()) {
            return List.of();
        }

        int threshold = properties.investigation().largeDependentThreshold();
        if (impact.dependents().size() >= threshold
                && !already.contains(ToolType.ANALYZE_TRANSITIVE_DEPENDENTS)) {
            suggested.add(ToolType.ANALYZE_TRANSITIVE_DEPENDENTS);
        }

        boolean hasPackageOrViewDependent = impact.dependents().stream()
                .anyMatch(InvestigationLoopService::isPackageOrView);
        if ((hasPackageOrViewDependent || impact.dependents().size() >= threshold)
                && !already.contains(ToolType.ANALYZE_SOURCE_REFERENCES)) {
            suggested.add(ToolType.ANALYZE_SOURCE_REFERENCES);
        }

        boolean hasJobDependent = impact.dependents().stream()
                .anyMatch(d -> "JOB".equalsIgnoreCase(d.objectType()));
        if ((hasJobDependent || !impact.schedulerJobs().isEmpty())
                && !already.contains(ToolType.ANALYZE_SCHEDULER_JOBS)) {
            suggested.add(ToolType.ANALYZE_SCHEDULER_JOBS);
        }

        boolean needsHealth = !"VALID".equalsIgnoreCase(impact.packageInfo().status())
                || impact.dependents().stream().anyMatch(d -> d.status() != null
                && !"VALID".equalsIgnoreCase(d.status()))
                || impact.dependencies().stream().anyMatch(d -> d.status() != null
                && !"VALID".equalsIgnoreCase(d.status()))
                || !impact.invalidRelatedObjects().isEmpty();
        if (needsHealth && !already.contains(ToolType.ANALYZE_RELATED_OBJECT_HEALTH)) {
            suggested.add(ToolType.ANALYZE_RELATED_OBJECT_HEALTH);
        }

        // Always allow approved follow-ups that heuristics selected and that are in the allowlist
        Set<ToolType> allowlist = EnumSet.copyOf(AiReasoningService.packageImpactFollowUpTools());
        allowlist.add(ToolType.ANALYZE_SCHEDULER_JOBS);
        allowlist.add(ToolType.ANALYZE_RELATED_OBJECT_HEALTH);

        List<ToolType> result = new ArrayList<>();
        for (ToolType tool : suggested) {
            if (allowlist.contains(tool) && !already.contains(tool)) {
                result.add(tool);
            }
        }
        return List.copyOf(result);
    }

    public String reasonFor(ToolType tool, ChangeReview review) {
        PackageDbImpact impact = review.getPackageDbImpact();
        int dependents = impact == null ? 0 : impact.dependents().size();
        return switch (tool) {
            case ANALYZE_TRANSITIVE_DEPENDENTS ->
                    "Follow-up: ANALYZE_TRANSITIVE_DEPENDENTS because " + dependents + " direct dependents";
            case ANALYZE_SOURCE_REFERENCES ->
                    "Follow-up: ANALYZE_SOURCE_REFERENCES because dependents include packages/views or blast radius is large";
            case ANALYZE_SCHEDULER_JOBS ->
                    "Follow-up: ANALYZE_SCHEDULER_JOBS because job dependents or operational scheduling risk was indicated";
            case ANALYZE_RELATED_OBJECT_HEALTH ->
                    "Follow-up: ANALYZE_RELATED_OBJECT_HEALTH because non-VALID related objects were indicated";
            default -> "Follow-up: " + tool;
        };
    }

    private static boolean isPackageOrView(DbObjectRef ref) {
        if (ref == null || ref.objectType() == null) {
            return false;
        }
        String t = ref.objectType().toUpperCase();
        return t.contains("PACKAGE") || "VIEW".equals(t);
    }
}
