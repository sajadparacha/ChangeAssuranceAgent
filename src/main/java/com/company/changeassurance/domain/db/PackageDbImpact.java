package com.company.changeassurance.domain.db;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Database-level blast radius for a PL/SQL package discovered from catalog metadata only.
 */
public record PackageDbImpact(
        boolean packageFound,
        String catalogMode,
        DbPackageInfo packageInfo,
        List<DbPackageProcedure> procedures,
        List<DbObjectRef> dependencies,
        List<DbObjectRef> dependents,
        List<DbTransitiveNode> transitiveDependents,
        List<DbSourceReference> sourceReferences,
        List<DbSchedulerJob> schedulerJobs,
        List<DbObjectRef> invalidRelatedObjects,
        Map<String, Long> dependencyCountsByType,
        Map<String, Long> dependentCountsByType,
        PackageImpactAssessment impactAssessment,
        String summary
) {
    public PackageDbImpact {
        Objects.requireNonNull(catalogMode, "catalogMode must not be null");
        procedures = List.copyOf(procedures == null ? List.of() : procedures);
        dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        dependents = List.copyOf(dependents == null ? List.of() : dependents);
        transitiveDependents = List.copyOf(transitiveDependents == null ? List.of() : transitiveDependents);
        sourceReferences = List.copyOf(sourceReferences == null ? List.of() : sourceReferences);
        schedulerJobs = List.copyOf(schedulerJobs == null ? List.of() : schedulerJobs);
        invalidRelatedObjects = List.copyOf(invalidRelatedObjects == null ? List.of() : invalidRelatedObjects);
        dependencyCountsByType = Map.copyOf(
                dependencyCountsByType == null ? Map.of() : dependencyCountsByType);
        dependentCountsByType = Map.copyOf(
                dependentCountsByType == null ? Map.of() : dependentCountsByType);
        summary = summary == null ? "" : summary;
    }

    public static PackageDbImpact notFound(String catalogMode, String owner, String packageName) {
        return new PackageDbImpact(
                false,
                catalogMode,
                new DbPackageInfo(owner, packageName, false, false, "NOT_FOUND"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                null,
                "Package " + (owner == null || owner.isBlank() ? packageName : owner + "." + packageName)
                        + " was not found in the " + catalogMode + " catalog"
        );
    }

    public static PackageDbImpact of(
            String catalogMode,
            DbPackageInfo info,
            List<DbPackageProcedure> procedures,
            List<DbObjectRef> dependencies,
            List<DbObjectRef> dependents
    ) {
        return of(
                catalogMode,
                info,
                procedures,
                dependencies,
                dependents,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    public static PackageDbImpact of(
            String catalogMode,
            DbPackageInfo info,
            List<DbPackageProcedure> procedures,
            List<DbObjectRef> dependencies,
            List<DbObjectRef> dependents,
            List<DbTransitiveNode> transitiveDependents,
            List<DbSourceReference> sourceReferences,
            List<DbSchedulerJob> schedulerJobs,
            List<DbObjectRef> invalidRelatedObjects,
            PackageImpactAssessment impactAssessment
    ) {
        Map<String, Long> depCounts = countByType(dependencies);
        Map<String, Long> dependentCounts = countByType(dependents);
        String summary = "Package " + info.qualifiedName()
                + " status=" + info.status()
                + "; procedures=" + procedures.size()
                + "; dependencies=" + dependencies.size()
                + "; dependents=" + dependents.size()
                + "; transitive=" + transitiveDependents.size()
                + "; jobs=" + schedulerJobs.size()
                + "; invalidRelated=" + invalidRelatedObjects.size();
        return new PackageDbImpact(
                true,
                catalogMode,
                info,
                procedures,
                dependencies,
                dependents,
                transitiveDependents,
                sourceReferences,
                schedulerJobs,
                invalidRelatedObjects,
                depCounts,
                dependentCounts,
                impactAssessment,
                summary
        );
    }

    public PackageDbImpact withProcedures(List<DbPackageProcedure> procedures) {
        return replace(procedures, dependencies, dependents, transitiveDependents,
                sourceReferences, schedulerJobs, invalidRelatedObjects, impactAssessment);
    }

    public PackageDbImpact withDependencies(List<DbObjectRef> dependencies) {
        return replace(procedures, dependencies, dependents, transitiveDependents,
                sourceReferences, schedulerJobs, invalidRelatedObjects, impactAssessment);
    }

    public PackageDbImpact withDependents(List<DbObjectRef> dependents) {
        return replace(procedures, dependencies, dependents, transitiveDependents,
                sourceReferences, schedulerJobs, invalidRelatedObjects, impactAssessment);
    }

    public PackageDbImpact withTransitiveDependents(List<DbTransitiveNode> transitiveDependents) {
        return replace(procedures, dependencies, dependents, transitiveDependents,
                sourceReferences, schedulerJobs, invalidRelatedObjects, impactAssessment);
    }

    public PackageDbImpact withSourceReferences(List<DbSourceReference> sourceReferences) {
        return replace(procedures, dependencies, dependents, transitiveDependents,
                sourceReferences, schedulerJobs, invalidRelatedObjects, impactAssessment);
    }

    public PackageDbImpact withSchedulerJobs(List<DbSchedulerJob> schedulerJobs) {
        return replace(procedures, dependencies, dependents, transitiveDependents,
                sourceReferences, schedulerJobs, invalidRelatedObjects, impactAssessment);
    }

    public PackageDbImpact withInvalidRelatedObjects(List<DbObjectRef> invalidRelatedObjects) {
        return replace(procedures, dependencies, dependents, transitiveDependents,
                sourceReferences, schedulerJobs, invalidRelatedObjects, impactAssessment);
    }

    public PackageDbImpact withImpactAssessment(PackageImpactAssessment impactAssessment) {
        return replace(procedures, dependencies, dependents, transitiveDependents,
                sourceReferences, schedulerJobs, invalidRelatedObjects, impactAssessment);
    }

    private PackageDbImpact replace(
            List<DbPackageProcedure> procedures,
            List<DbObjectRef> dependencies,
            List<DbObjectRef> dependents,
            List<DbTransitiveNode> transitiveDependents,
            List<DbSourceReference> sourceReferences,
            List<DbSchedulerJob> schedulerJobs,
            List<DbObjectRef> invalidRelatedObjects,
            PackageImpactAssessment impactAssessment
    ) {
        if (!packageFound) {
            return this;
        }
        return of(
                catalogMode,
                packageInfo,
                procedures,
                dependencies,
                dependents,
                transitiveDependents,
                sourceReferences,
                schedulerJobs,
                invalidRelatedObjects,
                impactAssessment
        );
    }

    private static Map<String, Long> countByType(List<DbObjectRef> objects) {
        return objects.stream()
                .collect(Collectors.groupingBy(
                        DbObjectRef::objectType,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }
}
