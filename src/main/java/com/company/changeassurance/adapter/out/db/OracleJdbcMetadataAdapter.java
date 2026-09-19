package com.company.changeassurance.adapter.out.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.company.changeassurance.application.port.out.DatabaseMetadataPort;
import com.company.changeassurance.domain.db.DbObjectRef;
import com.company.changeassurance.domain.db.DbPackageInfo;
import com.company.changeassurance.domain.db.DbPackageProcedure;
import com.company.changeassurance.domain.db.DbSchedulerJob;
import com.company.changeassurance.domain.db.DbSourceReference;
import com.company.changeassurance.domain.db.DbTransitiveNode;
import com.company.changeassurance.domain.exception.DomainValidationException;

/**
 * Read-only Oracle dictionary adapter (ALL_OBJECTS / ALL_DEPENDENCIES / ALL_PROCEDURES /
 * ALL_SOURCE / ALL_SCHEDULER_JOBS). Never executes change SQL. Fails closed when the driver
 * or connection is unavailable.
 */
public class OracleJdbcMetadataAdapter implements DatabaseMetadataPort {

    private static final Logger log = LoggerFactory.getLogger(OracleJdbcMetadataAdapter.class);

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String driverClassName;
    private final String defaultOwner;

    public OracleJdbcMetadataAdapter(
            String jdbcUrl,
            String username,
            String password,
            String driverClassName,
            String defaultOwner
    ) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("Oracle JDBC URL is required for db-metadata.mode=oracle");
        }
        this.jdbcUrl = jdbcUrl;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.driverClassName = driverClassName == null || driverClassName.isBlank()
                ? "oracle.jdbc.OracleDriver"
                : driverClassName;
        this.defaultOwner = defaultOwner == null || defaultOwner.isBlank()
                ? null
                : defaultOwner.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public String catalogMode() {
        return "oracle";
    }

    /**
     * Fail closed at startup if Oracle is unreachable (avoids silent fake-catalog behavior).
     */
    public void verifyConnectivity() {
        try (Connection ignored = open()) {
            log.info("Oracle catalog metadata connection OK ({})", jdbcUrl);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Oracle catalog metadata is required but connection failed for url="
                            + jdbcUrl + ": " + ex.getMessage(),
                    ex
            );
        }
    }

    @Override
    public Optional<DbPackageInfo> findPackage(String owner, String packageName) {
        String name = requireName(packageName);
        String resolvedOwner = resolveOwner(owner);
        String sql = """
                SELECT owner, object_name, object_type, status
                FROM all_objects
                WHERE object_name = ?
                  AND object_type IN ('PACKAGE', 'PACKAGE BODY')
                  AND (? IS NULL OR owner = ?)
                ORDER BY owner, object_type
                """;
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, resolvedOwner);
            ps.setString(3, resolvedOwner);
            boolean spec = false;
            boolean body = false;
            String foundOwner = resolvedOwner;
            String status = "UNKNOWN";
            Set<String> owners = new LinkedHashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String rowOwner = rs.getString("owner");
                    owners.add(rowOwner);
                    foundOwner = rowOwner;
                    String type = rs.getString("object_type");
                    status = rs.getString("status");
                    if ("PACKAGE".equalsIgnoreCase(type)) {
                        spec = true;
                    } else if ("PACKAGE BODY".equalsIgnoreCase(type)) {
                        body = true;
                    }
                }
            }
            if (!spec && !body) {
                return Optional.empty();
            }
            if (resolvedOwner == null && owners.size() > 1) {
                throw new DomainValidationException(
                        "Multiple schemas contain package " + name + "; specify schemaOwner");
            }
            return Optional.of(new DbPackageInfo(foundOwner, name, spec, body, status));
        } catch (DomainValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw catalogFailure(ex);
        }
    }

    @Override
    public List<DbPackageProcedure> listPackageProcedures(String owner, String packageName) {
        String name = requireName(packageName);
        String resolvedOwner = resolveOwner(owner);
        String sql = """
                SELECT DISTINCT procedure_name, object_type
                FROM all_procedures
                WHERE object_name = ?
                  AND (? IS NULL OR owner = ?)
                  AND procedure_name IS NOT NULL
                ORDER BY procedure_name
                """;
        List<DbPackageProcedure> result = new ArrayList<>();
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, resolvedOwner);
            ps.setString(3, resolvedOwner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String proc = rs.getString("procedure_name");
                    String type = rs.getString("object_type");
                    String procedureType = type != null && type.toUpperCase(Locale.ROOT).contains("FUNCTION")
                            ? "FUNCTION"
                            : "PROCEDURE";
                    result.add(new DbPackageProcedure(proc, procedureType));
                }
            }
            return result;
        } catch (Exception ex) {
            throw catalogFailure(ex);
        }
    }

    @Override
    public List<DbObjectRef> listDependencies(String owner, String packageName) {
        return queryDependencies(owner, packageName, true);
    }

    @Override
    public List<DbObjectRef> listDependents(String owner, String packageName) {
        return queryDependencies(owner, packageName, false);
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
        Optional<DbPackageInfo> found = findPackage(owner, packageName);
        if (found.isEmpty()) {
            return List.of();
        }
        DbPackageInfo info = found.get();
        List<DbTransitiveNode> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Queue<QueueNode> queue = new ArrayDeque<>();
        visited.add(key(info.owner(), info.packageName(), "PACKAGE"));

        for (DbObjectRef dependent : listDependents(info.owner(), info.packageName())) {
            String k = key(dependent.owner(), dependent.objectName(), dependent.objectType());
            if (visited.add(k)) {
                queue.add(new QueueNode(dependent, 1));
            }
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
            if (!isExpandableType(current.object().objectType())) {
                continue;
            }
            for (DbObjectRef next : listDependents(current.object().owner(), current.object().objectName())) {
                String nk = key(next.owner(), next.objectName(), next.objectType());
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
        String resolvedOwner = resolveOwner(owner);
        int limit = Math.max(1, Math.min(maxRows, 500));
        String sql = """
                SELECT owner, name, type, line, text
                FROM all_source
                WHERE UPPER(text) LIKE '%' || ? || '%'
                  AND (? IS NULL OR owner = ?)
                  AND ROWNUM <= ?
                ORDER BY owner, name, line
                """;
        List<DbSourceReference> result = new ArrayList<>();
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, needle);
            ps.setString(2, resolvedOwner);
            ps.setString(3, resolvedOwner);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new DbSourceReference(
                            rs.getString("owner"),
                            rs.getString("name"),
                            rs.getString("type"),
                            rs.getInt("line"),
                            rs.getString("text")
                    ));
                }
            }
            return List.copyOf(result);
        } catch (Exception ex) {
            throw catalogFailure(ex);
        }
    }

    @Override
    public List<DbSchedulerJob> listSchedulerJobsReferencing(String owner, String packageName) {
        String name = requireName(packageName);
        String resolvedOwner = resolveOwner(owner);
        String pattern = "%" + name + "%";
        String qualifiedPattern = resolvedOwner == null ? pattern : "%" + resolvedOwner + "." + name + "%";
        String sql = """
                SELECT owner, job_name, job_type, enabled, state, job_action
                FROM all_scheduler_jobs
                WHERE (UPPER(job_action) LIKE ? OR UPPER(job_action) LIKE ?)
                  AND (? IS NULL OR owner = ?)
                ORDER BY owner, job_name
                """;
        List<DbSchedulerJob> result = new ArrayList<>();
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, qualifiedPattern);
            ps.setString(3, resolvedOwner);
            ps.setString(4, resolvedOwner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new DbSchedulerJob(
                            rs.getString("owner"),
                            rs.getString("job_name"),
                            rs.getString("job_type"),
                            rs.getString("enabled"),
                            rs.getString("state"),
                            rs.getString("job_action")
                    ));
                }
            }
            return List.copyOf(result);
        } catch (Exception ex) {
            throw catalogFailure(ex);
        }
    }

    @Override
    public List<DbObjectRef> listInvalidRelatedObjects(String owner, String packageName) {
        Optional<DbPackageInfo> found = findPackage(owner, packageName);
        if (found.isEmpty()) {
            return List.of();
        }
        DbPackageInfo info = found.get();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<DbObjectRef> invalid = new ArrayList<>();
        if (!"VALID".equalsIgnoreCase(info.status())) {
            DbObjectRef self = new DbObjectRef(info.owner(), info.packageName(), "PACKAGE", info.status());
            seen.add(key(self.owner(), self.objectName(), self.objectType()));
            invalid.add(self);
        }
        for (DbObjectRef ref : listDependencies(info.owner(), info.packageName())) {
            if (ref.status() != null && !"VALID".equalsIgnoreCase(ref.status())
                    && seen.add(key(ref.owner(), ref.objectName(), ref.objectType()))) {
                invalid.add(ref);
            }
        }
        for (DbObjectRef ref : listDependents(info.owner(), info.packageName())) {
            if (ref.status() != null && !"VALID".equalsIgnoreCase(ref.status())
                    && seen.add(key(ref.owner(), ref.objectName(), ref.objectType()))) {
                invalid.add(ref);
            }
        }
        return List.copyOf(invalid);
    }

    private List<DbObjectRef> queryDependencies(String owner, String packageName, boolean outbound) {
        String name = requireName(packageName);
        String resolvedOwner = resolveOwner(owner);
        String sql = outbound
                ? """
                  SELECT d.referenced_owner AS obj_owner,
                         d.referenced_name AS obj_name,
                         d.referenced_type AS obj_type,
                         NVL(o.status, 'UNKNOWN') AS status
                  FROM all_dependencies d
                  LEFT JOIN all_objects o
                    ON o.owner = d.referenced_owner
                   AND o.object_name = d.referenced_name
                   AND o.object_type = d.referenced_type
                  WHERE d.name = ?
                    AND d.type IN ('PACKAGE', 'PACKAGE BODY')
                    AND (? IS NULL OR d.owner = ?)
                  ORDER BY d.referenced_owner, d.referenced_name
                  """
                : """
                  SELECT d.owner AS obj_owner,
                         d.name AS obj_name,
                         d.type AS obj_type,
                         NVL(o.status, 'UNKNOWN') AS status
                  FROM all_dependencies d
                  LEFT JOIN all_objects o
                    ON o.owner = d.owner
                   AND o.object_name = d.name
                   AND o.object_type = d.type
                  WHERE d.referenced_name = ?
                    AND d.referenced_type IN ('PACKAGE', 'PACKAGE BODY')
                    AND (? IS NULL OR d.referenced_owner = ?)
                  ORDER BY d.owner, d.name
                  """;
        List<DbObjectRef> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, resolvedOwner);
            ps.setString(3, resolvedOwner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DbObjectRef ref = new DbObjectRef(
                            rs.getString("obj_owner"),
                            rs.getString("obj_name"),
                            rs.getString("obj_type"),
                            rs.getString("status")
                    );
                    String key = key(ref.owner(), ref.objectName(), ref.objectType());
                    if (seen.add(key)) {
                        result.add(ref);
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            throw catalogFailure(ex);
        }
    }

    private Connection open() throws SQLException, ClassNotFoundException {
        Class.forName(driverClassName);
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private String resolveOwner(String owner) {
        if (owner != null && !owner.isBlank()) {
            return owner.trim().toUpperCase(Locale.ROOT);
        }
        return defaultOwner;
    }

    private static String requireName(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            throw new IllegalArgumentException("packageName must not be blank");
        }
        return packageName.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isExpandableType(String objectType) {
        if (objectType == null) {
            return false;
        }
        String t = objectType.toUpperCase(Locale.ROOT);
        return t.contains("PACKAGE") || "VIEW".equals(t) || "PROCEDURE".equals(t)
                || "FUNCTION".equals(t) || "TRIGGER".equals(t) || "SYNONYM".equals(t);
    }

    private static String key(String owner, String name, String type) {
        return (owner == null ? "" : owner) + "." + name + ":" + type;
    }

    private DomainValidationException catalogFailure(Exception ex) {
        log.warn("Oracle metadata catalog query failed: {}", ex.getMessage());
        return new DomainValidationException(
                "Oracle metadata catalog unavailable: " + ex.getMessage());
    }

    private record QueueNode(DbObjectRef object, int depth) {
    }
}
