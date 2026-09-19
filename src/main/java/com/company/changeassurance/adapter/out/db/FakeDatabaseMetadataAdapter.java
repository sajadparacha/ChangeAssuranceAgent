package com.company.changeassurance.adapter.out.db;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import com.company.changeassurance.application.port.out.DatabaseMetadataPort;
import com.company.changeassurance.domain.db.DbObjectRef;
import com.company.changeassurance.domain.db.DbPackageInfo;
import com.company.changeassurance.domain.db.DbPackageProcedure;
import com.company.changeassurance.domain.db.DbSchedulerJob;
import com.company.changeassurance.domain.db.DbSourceReference;
import com.company.changeassurance.domain.db.DbTransitiveNode;

/**
 * In-memory Oracle-style catalog for standalone demos and tests.
 * Seeded around BILLING_PKG from the sample deploy script.
 */
public class FakeDatabaseMetadataAdapter implements DatabaseMetadataPort {

    private final Map<String, SeededPackage> packages = new LinkedHashMap<>();
    private final List<DbSourceReference> sourceIndex = new ArrayList<>();
    private final List<DbSchedulerJob> schedulerJobs = new ArrayList<>();
    private final Map<String, List<DbObjectRef>> extraDependents = new LinkedHashMap<>();
    private final String defaultOwner;

    public FakeDatabaseMetadataAdapter() {
        this("APP");
    }

    public FakeDatabaseMetadataAdapter(String defaultOwner) {
        this.defaultOwner = defaultOwner == null || defaultOwner.isBlank()
                ? "APP"
                : defaultOwner.trim().toUpperCase(Locale.ROOT);
        seed();
    }

    private void seed() {
        packages.put(key(defaultOwner, "BILLING_PKG"), new SeededPackage(
                new DbPackageInfo(defaultOwner, "BILLING_PKG", true, true, "VALID"),
                List.of(
                        new DbPackageProcedure("REFRESH_ACCOUNTS", "PROCEDURE"),
                        new DbPackageProcedure("GET_STATUS", "FUNCTION")
                ),
                List.of(
                        new DbObjectRef(defaultOwner, "BILLING_ACCOUNTS", "TABLE", "VALID"),
                        new DbObjectRef(defaultOwner, "BILLING_ACCOUNT_STATUS", "VIEW", "VALID"),
                        new DbObjectRef("SYS", "DBMS_STANDARD", "PACKAGE", "VALID")
                ),
                List.of(
                        new DbObjectRef(defaultOwner, "BILLING_REFRESH_JOB", "JOB", "VALID"),
                        new DbObjectRef(defaultOwner, "TRG_BILLING_ACCOUNTS_AI", "TRIGGER", "VALID"),
                        new DbObjectRef(defaultOwner, "API_BILLING_WRAPPER", "PACKAGE", "VALID"),
                        new DbObjectRef(defaultOwner, "V_BILLING_STATUS", "VIEW", "VALID")
                )
        ));
        packages.put(key(defaultOwner, "API_BILLING_WRAPPER"), new SeededPackage(
                new DbPackageInfo(defaultOwner, "API_BILLING_WRAPPER", true, true, "VALID"),
                List.of(new DbPackageProcedure("REFRESH", "PROCEDURE")),
                List.of(new DbObjectRef(defaultOwner, "BILLING_PKG", "PACKAGE", "VALID")),
                List.of(
                        new DbObjectRef(defaultOwner, "BILLING_API_GATEWAY", "PACKAGE", "VALID"),
                        new DbObjectRef(defaultOwner, "BATCH_BILLING_CALLER", "PROCEDURE", "INVALID")
                )
        ));
        packages.put(key(defaultOwner, "BILLING_API_GATEWAY"), new SeededPackage(
                new DbPackageInfo(defaultOwner, "BILLING_API_GATEWAY", true, true, "VALID"),
                List.of(new DbPackageProcedure("INVOKE", "PROCEDURE")),
                List.of(new DbObjectRef(defaultOwner, "API_BILLING_WRAPPER", "PACKAGE", "VALID")),
                List.of(new DbObjectRef(defaultOwner, "EXT_BILLING_CLIENT", "SYNONYM", "VALID"))
        ));
        packages.put(key(defaultOwner, "ORDERS_PKG"), new SeededPackage(
                new DbPackageInfo(defaultOwner, "ORDERS_PKG", true, true, "VALID"),
                List.of(
                        new DbPackageProcedure("PLACE_ORDER", "PROCEDURE"),
                        new DbPackageProcedure("CANCEL_ORDER", "PROCEDURE")
                ),
                List.of(
                        new DbObjectRef(defaultOwner, "ORDERS", "TABLE", "VALID"),
                        new DbObjectRef(defaultOwner, "ORDER_LINES", "TABLE", "VALID")
                ),
                List.of(
                        new DbObjectRef(defaultOwner, "ORDER_FULFILLMENT_JOB", "JOB", "VALID")
                )
        ));
        packages.put(key(defaultOwner, "AUDIT_PKG"), new SeededPackage(
                new DbPackageInfo(defaultOwner, "AUDIT_PKG", true, false, "INVALID"),
                List.of(new DbPackageProcedure("WRITE_EVENT", "PROCEDURE")),
                List.of(new DbObjectRef(defaultOwner, "AUDIT_EVENTS", "TABLE", "VALID")),
                List.of()
        ));
        packages.put(key(defaultOwner, "USERS_PKG"), new SeededPackage(
                new DbPackageInfo(defaultOwner, "USERS_PKG", true, true, "VALID"),
                List.of(
                        new DbPackageProcedure("CREATE_USER", "PROCEDURE"),
                        new DbPackageProcedure("GET_USER", "PROCEDURE"),
                        new DbPackageProcedure("USER_EXISTS", "FUNCTION"),
                        new DbPackageProcedure("UPDATE_USER", "PROCEDURE"),
                        new DbPackageProcedure("UPDATE_PASSWORD", "PROCEDURE"),
                        new DbPackageProcedure("DELETE_USER", "PROCEDURE"),
                        new DbPackageProcedure("CLOSE_USER_ACCOUNT", "PROCEDURE")
                ),
                List.of(
                        new DbObjectRef(defaultOwner, "USERS", "TABLE", "VALID"),
                        new DbObjectRef(defaultOwner, "USER_ORDER_DETAILS", "TABLE", "VALID"),
                        new DbObjectRef(defaultOwner, "ORDER_LINES", "TABLE", "VALID"),
                        new DbObjectRef(defaultOwner, "V_USERS_REPORT", "VIEW", "VALID"),
                        new DbObjectRef(defaultOwner, "ORDERS_PKG", "PACKAGE", "VALID"),
                        new DbObjectRef(defaultOwner, "AUDIT_PKG", "PACKAGE", "VALID"),
                        new DbObjectRef(defaultOwner, "USERS_USER_ID_SEQ", "SEQUENCE", "VALID")
                ),
                List.of(
                        new DbObjectRef(defaultOwner, "CLOSE_USER_ACCOUNT_JOB", "PROCEDURE", "VALID")
                )
        ));

        extraDependents.put(key(defaultOwner, "V_BILLING_STATUS"), List.of(
                new DbObjectRef(defaultOwner, "RPT_BILLING_DASHBOARD", "VIEW", "VALID")
        ));

        sourceIndex.add(new DbSourceReference(
                defaultOwner, "API_BILLING_WRAPPER", "PACKAGE BODY", 12,
                "BEGIN APP.BILLING_PKG.REFRESH_ACCOUNTS; END;"));
        sourceIndex.add(new DbSourceReference(
                defaultOwner, "TRG_BILLING_ACCOUNTS_AI", "TRIGGER", 8,
                "IF UPDATING THEN APP.BILLING_PKG.GET_STATUS(:NEW.ID); END IF;"));
        sourceIndex.add(new DbSourceReference(
                defaultOwner, "BATCH_BILLING_CALLER", "PROCEDURE", 4,
                "APP.BILLING_PKG.REFRESH_ACCOUNTS;"));
        sourceIndex.add(new DbSourceReference(
                defaultOwner, "CLOSE_USER_ACCOUNT_JOB", "PROCEDURE", 3,
                "APP.USERS_PKG.CLOSE_USER_ACCOUNT(p_networkid => p_networkid);"));

        schedulerJobs.add(new DbSchedulerJob(
                defaultOwner,
                "BILLING_NIGHTLY_REFRESH",
                "PLSQL_BLOCK",
                "TRUE",
                "SCHEDULED",
                "BEGIN APP.BILLING_PKG.REFRESH_ACCOUNTS; END;"
        ));
        schedulerJobs.add(new DbSchedulerJob(
                defaultOwner,
                "BILLING_STATUS_PROBE",
                "STORED_PROCEDURE",
                "TRUE",
                "DISABLED",
                "APP.BILLING_PKG.GET_STATUS"
        ));
        schedulerJobs.add(new DbSchedulerJob(
                defaultOwner,
                "ORDERS_FULFILLMENT",
                "PLSQL_BLOCK",
                "TRUE",
                "SCHEDULED",
                "BEGIN APP.ORDERS_PKG.PLACE_ORDER(1); END;"
        ));
        schedulerJobs.add(new DbSchedulerJob(
                defaultOwner,
                "USERS_ACCOUNT_CLOSE_SWEEP",
                "PLSQL_BLOCK",
                "FALSE",
                "DISABLED",
                "BEGIN APP.USERS_PKG.CLOSE_USER_ACCOUNT('NET000'); END;"
        ));
    }

    @Override
    public String catalogMode() {
        return "fake";
    }

    @Override
    public Optional<DbPackageInfo> findPackage(String owner, String packageName) {
        return resolve(owner, packageName).map(SeededPackage::info);
    }

    @Override
    public List<DbPackageProcedure> listPackageProcedures(String owner, String packageName) {
        return resolve(owner, packageName).map(p -> List.copyOf(p.procedures())).orElse(List.of());
    }

    @Override
    public List<DbObjectRef> listDependencies(String owner, String packageName) {
        return resolve(owner, packageName).map(p -> List.copyOf(p.dependencies())).orElse(List.of());
    }

    @Override
    public List<DbObjectRef> listDependents(String owner, String packageName) {
        return resolve(owner, packageName).map(p -> List.copyOf(p.dependents())).orElse(List.of());
    }

    @Override
    public List<DbTransitiveNode> listTransitiveDependents(
            String owner,
            String packageName,
            int maxDepth,
            int maxNodes
    ) {
        int depthCap = Math.max(2, maxDepth);
        int nodeCap = Math.max(1, maxNodes);
        Optional<SeededPackage> root = resolve(owner, packageName);
        if (root.isEmpty()) {
            return List.of();
        }

        List<DbTransitiveNode> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Queue<QueueNode> queue = new ArrayDeque<>();
        String rootKey = key(root.get().info().owner(), root.get().info().packageName());
        visited.add(rootKey);

        for (DbObjectRef dependent : root.get().dependents()) {
            queue.add(new QueueNode(dependent, 1));
            visited.add(refKey(dependent));
        }

        while (!queue.isEmpty() && result.size() < nodeCap) {
            QueueNode current = queue.poll();
            if (current.depth() >= 2) {
                result.add(new DbTransitiveNode(current.object(), current.depth()));
                if (result.size() >= nodeCap) {
                    break;
                }
            }
            if (current.depth() >= depthCap) {
                continue;
            }
            for (DbObjectRef next : neighbors(current.object())) {
                String nk = refKey(next);
                if (visited.add(nk)) {
                    queue.add(new QueueNode(next, current.depth() + 1));
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public List<DbSourceReference> searchSourceReferences(String owner, String searchText, int maxRows) {
        if (searchText == null || searchText.isBlank()) {
            return List.of();
        }
        String needle = searchText.trim().toUpperCase(Locale.ROOT);
        String ownerFilter = owner == null || owner.isBlank()
                ? null
                : owner.trim().toUpperCase(Locale.ROOT);
        int limit = Math.max(1, maxRows);
        List<DbSourceReference> hits = new ArrayList<>();
        for (DbSourceReference ref : sourceIndex) {
            if (ownerFilter != null && (ref.owner() == null || !ownerFilter.equals(ref.owner()))) {
                continue;
            }
            String haystack = (ref.qualifiedName() + " " + ref.excerpt()).toUpperCase(Locale.ROOT);
            if (haystack.contains(needle) || needle.contains(ref.objectName())) {
                hits.add(ref);
                if (hits.size() >= limit) {
                    break;
                }
            }
        }
        return List.copyOf(hits);
    }

    @Override
    public List<DbSchedulerJob> listSchedulerJobsReferencing(String owner, String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return List.of();
        }
        String name = packageName.trim().toUpperCase(Locale.ROOT);
        String ownerFilter = owner == null || owner.isBlank()
                ? defaultOwner
                : owner.trim().toUpperCase(Locale.ROOT);
        List<DbSchedulerJob> hits = new ArrayList<>();
        for (DbSchedulerJob job : schedulerJobs) {
            String action = job.actionExcerpt() == null ? "" : job.actionExcerpt().toUpperCase(Locale.ROOT);
            boolean nameHit = action.contains(name) || action.contains(ownerFilter + "." + name);
            boolean ownerOk = job.owner() == null || ownerFilter.equals(job.owner());
            if (nameHit && ownerOk) {
                hits.add(job);
            }
        }
        return List.copyOf(hits);
    }

    @Override
    public List<DbObjectRef> listInvalidRelatedObjects(String owner, String packageName) {
        Optional<SeededPackage> found = resolve(owner, packageName);
        if (found.isEmpty()) {
            return List.of();
        }
        SeededPackage seeded = found.get();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<DbObjectRef> invalid = new ArrayList<>();
        addIfInvalid(invalid, seen, new DbObjectRef(
                seeded.info().owner(),
                seeded.info().packageName(),
                "PACKAGE",
                seeded.info().status()
        ));
        for (DbObjectRef ref : seeded.dependencies()) {
            addIfInvalid(invalid, seen, ref);
        }
        for (DbObjectRef ref : seeded.dependents()) {
            addIfInvalid(invalid, seen, ref);
            for (DbObjectRef neighbor : neighbors(ref)) {
                addIfInvalid(invalid, seen, neighbor);
            }
        }
        return List.copyOf(invalid);
    }

    private List<DbObjectRef> neighbors(DbObjectRef object) {
        List<DbObjectRef> result = new ArrayList<>();
        Optional<SeededPackage> pkg = resolve(object.owner(), object.objectName());
        pkg.ifPresent(seededPackage -> result.addAll(seededPackage.dependents()));
        List<DbObjectRef> extras = extraDependents.get(key(
                object.owner() == null ? defaultOwner : object.owner(),
                object.objectName()));
        if (extras != null) {
            result.addAll(extras);
        }
        return result;
    }

    private static void addIfInvalid(List<DbObjectRef> invalid, Set<String> seen, DbObjectRef ref) {
        if (ref.status() != null && !"VALID".equalsIgnoreCase(ref.status()) && seen.add(refKey(ref))) {
            invalid.add(ref);
        }
    }

    private Optional<SeededPackage> resolve(String owner, String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return Optional.empty();
        }
        String name = packageName.trim().toUpperCase(Locale.ROOT);
        String resolvedOwner = owner == null || owner.isBlank()
                ? defaultOwner
                : owner.trim().toUpperCase(Locale.ROOT);
        SeededPackage exact = packages.get(key(resolvedOwner, name));
        if (exact != null) {
            return Optional.of(exact);
        }
        List<SeededPackage> matches = new ArrayList<>();
        for (SeededPackage seeded : packages.values()) {
            if (seeded.info().packageName().equals(name)) {
                matches.add(seeded);
            }
        }
        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }
        return Optional.empty();
    }

    private static String key(String owner, String packageName) {
        return owner + "." + packageName;
    }

    private static String refKey(DbObjectRef ref) {
        return (ref.owner() == null ? "" : ref.owner()) + "." + ref.objectName() + ":" + ref.objectType();
    }

    private record SeededPackage(
            DbPackageInfo info,
            List<DbPackageProcedure> procedures,
            List<DbObjectRef> dependencies,
            List<DbObjectRef> dependents
    ) {
    }

    private record QueueNode(DbObjectRef object, int depth) {
    }
}
