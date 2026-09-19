package com.company.changeassurance.domain.db;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic impact assessment derived from Oracle catalog evidence.
 * AI may explain these facts but must not override {@link #overallImpact()}.
 */
public record PackageImpactAssessment(
        ImpactLevel overallImpact,
        List<String> why,
        List<String> recommendedTesting,
        String summary
) {
    public PackageImpactAssessment {
        Objects.requireNonNull(overallImpact, "overallImpact must not be null");
        why = List.copyOf(why == null ? List.of() : why);
        recommendedTesting = List.copyOf(recommendedTesting == null ? List.of() : recommendedTesting);
        summary = summary == null ? "" : summary;
    }

    public static PackageImpactAssessment unavailable(String reason) {
        return new PackageImpactAssessment(
                ImpactLevel.MEDIUM,
                List.of(reason == null ? "Impact assessment unavailable" : reason),
                List.of(
                        "Compile the target package specification and body",
                        "Manually identify dependents and schedule regression tests"
                ),
                reason == null ? "Impact assessment unavailable" : reason
        );
    }
}
