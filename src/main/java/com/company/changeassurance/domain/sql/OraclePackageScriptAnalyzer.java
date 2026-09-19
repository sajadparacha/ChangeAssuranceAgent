package com.company.changeassurance.domain.sql;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts Oracle package deploy deltas (spec vs body) from parsed SQL.
 * Does not scan application repositories.
 */
public final class OraclePackageScriptAnalyzer {

    private static final Pattern QUALIFIED_PACKAGE = Pattern.compile(
            "(?i)\\bPACKAGE(?:\\s+BODY)?\\s+"
                    + "(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?"
                    + "(?:\"?([A-Z0-9_$#]+)\"?\\.)?\"?([A-Z0-9_$#]+)\"?");

    private static final Pattern DROP_PACKAGE = Pattern.compile(
            "(?i)\\bDROP\\s+PACKAGE(?:\\s+BODY)?\\s+"
                    + "(?:IF\\s+EXISTS\\s+)?"
                    + "(?:\"?([A-Z0-9_$#]+)\"?\\.)?\"?([A-Z0-9_$#]+)\"?");

    private OraclePackageScriptAnalyzer() {
    }

    public static PackageDeployDelta analyze(SqlParseResult parseResult) {
        if (parseResult == null || parseResult.statements().isEmpty()) {
            return PackageDeployDelta.none();
        }

        PackageDeployDelta.Builder builder = new PackageDeployDelta.Builder();
        boolean anyPackageOp = false;

        for (SqlStatementInfo stmt : parseResult.statements()) {
            SqlOperationType op = stmt.operationType();
            if (op == SqlOperationType.PACKAGE_SPEC) {
                anyPackageOp = true;
                addTouches(builder, stmt, true, false);
            } else if (op == SqlOperationType.PACKAGE_BODY) {
                anyPackageOp = true;
                addTouches(builder, stmt, false, true);
            } else if (op == SqlOperationType.DROP && containsPackageKeyword(stmt.rawText())) {
                anyPackageOp = true;
                boolean bodyOnly = stmt.rawText().toUpperCase(Locale.ROOT).contains("PACKAGE BODY");
                addDropTouches(builder, stmt, !bodyOnly, bodyOnly);
            }
        }

        if (!anyPackageOp) {
            return PackageDeployDelta.none();
        }
        return builder.inferredFromScript(true).build();
    }

    private static void addTouches(
            PackageDeployDelta.Builder builder,
            SqlStatementInfo stmt,
            boolean spec,
            boolean body
    ) {
        List<String> names = stmt.objectNames();
        List<String> schemas = stmt.objectSchemas();
        if (names.isEmpty()) {
            // Fall back to regex on raw text when object list was empty
            Matcher m = QUALIFIED_PACKAGE.matcher(stmt.rawText());
            while (m.find()) {
                builder.touch(m.group(2), m.group(1), spec, body);
            }
            return;
        }
        for (int i = 0; i < names.size(); i++) {
            String schema = i < schemas.size() ? schemas.get(i) : null;
            builder.touch(names.get(i), schema, spec, body);
        }
    }

    private static void addDropTouches(
            PackageDeployDelta.Builder builder,
            SqlStatementInfo stmt,
            boolean spec,
            boolean body
    ) {
        Matcher m = DROP_PACKAGE.matcher(stmt.rawText());
        boolean matched = false;
        while (m.find()) {
            matched = true;
            builder.touch(m.group(2), m.group(1), spec, body);
        }
        if (!matched) {
            addTouches(builder, stmt, spec, body);
        }
    }

    private static boolean containsPackageKeyword(String raw) {
        if (raw == null) {
            return false;
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        return upper.contains("PACKAGE");
    }
}
