package com.company.changeassurance.application.port.out;

import java.util.List;
import java.util.Optional;

import com.company.changeassurance.domain.db.DbObjectRef;
import com.company.changeassurance.domain.db.DbPackageInfo;
import com.company.changeassurance.domain.db.DbPackageProcedure;
import com.company.changeassurance.domain.db.DbSchedulerJob;
import com.company.changeassurance.domain.db.DbSourceReference;
import com.company.changeassurance.domain.db.DbTransitiveNode;
import com.company.changeassurance.domain.db.PackageDbImpact;

/**
 * Read-only database catalog access for package impact analysis.
 * Implementations must never execute change SQL / DML / DDL.
 */
public interface DatabaseMetadataPort {

    /** Catalog mode label for reports: {@code fake} or {@code oracle}. */
    String catalogMode();

    Optional<DbPackageInfo> findPackage(String owner, String packageName);

    List<DbPackageProcedure> listPackageProcedures(String owner, String packageName);

    /** Objects the package references. */
    List<DbObjectRef> listDependencies(String owner, String packageName);

    /** Objects that depend on the package (blast radius). */
    List<DbObjectRef> listDependents(String owner, String packageName);

    /**
     * Dependents beyond 1-hop, breadth-first, capped by depth and node count.
     * Depth starts at 2 for the first transitive layer.
     */
    List<DbTransitiveNode> listTransitiveDependents(
            String owner,
            String packageName,
            int maxDepth,
            int maxNodes
    );

    /**
     * Parameterized source dictionary search. Implementations must never accept raw SQL.
     */
    List<DbSourceReference> searchSourceReferences(String owner, String searchText, int maxRows);

    /** Scheduler jobs whose action/program text references the package. */
    List<DbSchedulerJob> listSchedulerJobsReferencing(String owner, String packageName);

    /** Non-VALID objects among the target package and its 1-hop neighbors. */
    List<DbObjectRef> listInvalidRelatedObjects(String owner, String packageName);

    default PackageDbImpact analyzeImpact(String owner, String packageName) {
        return analyzeImpact(owner, packageName, 3, 50, 100);
    }

    default PackageDbImpact analyzeImpact(
            String owner,
            String packageName,
            int transitiveMaxDepth,
            int transitiveMaxNodes,
            int sourceMaxRows
    ) {
        if (packageName == null || packageName.isBlank()) {
            throw new IllegalArgumentException("packageName must not be blank");
        }
        Optional<DbPackageInfo> found = findPackage(owner, packageName);
        if (found.isEmpty()) {
            return PackageDbImpact.notFound(catalogMode(), owner, packageName.trim());
        }
        DbPackageInfo info = found.get();
        String resolvedOwner = info.owner();
        String resolvedName = info.packageName();
        List<DbObjectRef> dependencies = listDependencies(resolvedOwner, resolvedName);
        List<DbObjectRef> dependents = listDependents(resolvedOwner, resolvedName);
        return PackageDbImpact.of(
                catalogMode(),
                info,
                listPackageProcedures(resolvedOwner, resolvedName),
                dependencies,
                dependents,
                listTransitiveDependents(resolvedOwner, resolvedName, transitiveMaxDepth, transitiveMaxNodes),
                searchSourceReferences(resolvedOwner, resolvedName, sourceMaxRows),
                listSchedulerJobsReferencing(resolvedOwner, resolvedName),
                listInvalidRelatedObjects(resolvedOwner, resolvedName),
                null
        );
    }
}
