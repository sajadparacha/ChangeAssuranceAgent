package com.company.changeassurance.domain.sql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layered, deterministic SQL/PLSQL analyzer. Never executes SQL.
 *
 * <pre>
 * File preprocessing → statement/block separation → syntax-aware heuristics → rules → unsupported reporting
 * </pre>
 *
 * Regular expressions alone are not treated as a full parser; unsupported or ambiguous
 * constructs are reported rather than silently ignored.
 */
public final class SqlScriptParser {

    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)(password\\s*=\\s*['\"][^'\"]+['\"]|identified\\s+by\\s+\\S+|pwd\\s*=\\s*\\S+)");
    private static final Pattern HARD_SCHEMA = Pattern.compile(
            "(?i)\\b([A-Z][A-Z0-9_$#]*)\\.(?:[A-Z][A-Z0-9_$#]*)\\b");
    private static final Pattern OBJECT_AFTER = Pattern.compile(
            "(?i)\\b(?:TABLE|INDEX|VIEW|SEQUENCE|PACKAGE(?:\\s+BODY)?|PROCEDURE|FUNCTION|TRIGGER|TYPE)\\s+"
                    + "(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?"
                    + "(?:\"?([A-Z0-9_$#]+)\"?\\.)?\"?([A-Z0-9_$#]+)\"?");
    private static final Pattern UPDATE_TARGET = Pattern.compile(
            "(?i)\\bUPDATE\\s+(?:\"?([A-Z0-9_$#]+)\"?\\.)?\"?([A-Z0-9_$#]+)\"?");
    private static final Pattern DELETE_TARGET = Pattern.compile(
            "(?i)\\bDELETE\\s+FROM\\s+(?:\"?([A-Z0-9_$#]+)\"?\\.)?\"?([A-Z0-9_$#]+)\"?");
    private static final Pattern DROP_TARGET = Pattern.compile(
            "(?i)\\bDROP\\s+(?:TABLE|INDEX|VIEW|SEQUENCE|PACKAGE(?:\\s+BODY)?|PROCEDURE|FUNCTION|TRIGGER)\\s+"
                    + "(?:IF\\s+EXISTS\\s+)?"
                    + "(?:\"?([A-Z0-9_$#]+)\"?\\.)?\"?([A-Z0-9_$#]+)\"?");
    private static final Pattern GRANT_REVOKE = Pattern.compile("(?i)\\b(GRANT|REVOKE)\\b");
    private static final Pattern DYNAMIC_SQL = Pattern.compile("(?i)\\b(EXECUTE\\s+IMMEDIATE|DBMS_SQL)\\b");
    private static final Pattern WHERE = Pattern.compile("(?i)\\bWHERE\\b");

    public SqlParseResult parse(String content, String filename) {
        String name = filename == null || filename.isBlank() ? "uploaded.sql" : filename;
        List<String> notes = new ArrayList<>();
        if (content == null || content.isBlank()) {
            notes.add("SQL content was empty");
            return new SqlParseResult(name, false, List.of(), List.of("Empty SQL content"), notes);
        }

        String preprocessed = preprocess(content, notes);
        List<Segment> segments = splitStatements(preprocessed);
        List<SqlStatementInfo> statements = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();

        int index = 0;
        for (Segment segment : segments) {
            index++;
            SqlStatementInfo info = analyzeSegment(index, segment);
            statements.add(info);
            if (info.unsupported()) {
                unsupported.add("Statement " + index + " (line " + segment.startLine + "): " + info.unsupportedReason());
            }
        }

        boolean fullySupported = unsupported.isEmpty();
        return new SqlParseResult(name, fullySupported, statements, unsupported, notes);
    }

    private String preprocess(String content, List<String> notes) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        // Strip single-line -- comments but keep line numbers stable by replacing with spaces of same length? 
        // We keep content for analysis but strip comment interiors so injection in comments is data only.
        StringBuilder sb = new StringBuilder(normalized.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            char next = i + 1 < normalized.length() ? normalized.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    sb.append(c);
                } else {
                    sb.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    sb.append("  ");
                    i++;
                    inBlockComment = false;
                } else if (c == '\n') {
                    sb.append('\n');
                } else {
                    sb.append(' ');
                }
                continue;
            }
            if (!inSingle && !inDouble && c == '-' && next == '-') {
                inLineComment = true;
                sb.append(' ').append(' ');
                i++;
                continue;
            }
            if (!inSingle && !inDouble && c == '/' && next == '*') {
                inBlockComment = true;
                sb.append(' ').append(' ');
                i++;
                notes.add("Block comment removed during preprocessing (content treated as data, not instruction)");
                continue;
            }
            if (!inDouble && c == '\'' && !inSingle) {
                inSingle = true;
                sb.append(c);
                continue;
            }
            if (inSingle) {
                sb.append(c);
                if (c == '\'' && next == '\'') {
                    sb.append(next);
                    i++;
                } else if (c == '\'') {
                    inSingle = false;
                }
                continue;
            }
            if (!inSingle && c == '"' && !inDouble) {
                inDouble = true;
                sb.append(c);
                continue;
            }
            if (inDouble) {
                sb.append(c);
                if (c == '"') {
                    inDouble = false;
                }
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private List<Segment> splitStatements(String content) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int line = 1;
        int startLine = 1;
        boolean inSingle = false;
        boolean inDouble = false;
        // CREATE PACKAGE/BODY/PROCEDURE/FUNCTION/TRIGGER/TYPE stays open until '/' —
        // nested END of inner routines must not close the unit (avoids false SQL-009).
        boolean inCreateUnit = false;
        int beginDepth = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n') {
                line++;
            }
            if (!inDouble && c == '\'' && !inSingle) {
                inSingle = true;
                current.append(c);
                continue;
            }
            if (inSingle) {
                current.append(c);
                if (c == '\'' && i + 1 < content.length() && content.charAt(i + 1) == '\'') {
                    current.append(content.charAt(++i));
                } else if (c == '\'') {
                    inSingle = false;
                }
                continue;
            }
            if (!inSingle && c == '"' && !inDouble) {
                inDouble = true;
                current.append(c);
                continue;
            }
            if (inDouble) {
                current.append(c);
                if (c == '"') {
                    inDouble = false;
                }
                continue;
            }

            String ahead = content.substring(i, Math.min(content.length(), i + 32)).toUpperCase(Locale.ROOT);
            if (!inCreateUnit && isCreateKeyword(ahead)) {
                String window = content.substring(i, Math.min(content.length(), i + 140)).toUpperCase(Locale.ROOT);
                if (isPlsqlCreateUnit(window)) {
                    inCreateUnit = true;
                    beginDepth = 0;
                }
            }

            if (isKeywordAt(ahead, "BEGIN")) {
                beginDepth++;
            }

            if (isKeywordAt(ahead, "END")) {
                int endSemi = indexOfStatementEnd(content, i);
                if (endSemi >= 0) {
                    if (beginDepth > 0) {
                        beginDepth--;
                    }
                    current.append(content, i, endSemi + 1);
                    i = endSemi;
                    // Anonymous BEGIN..END only — never close CREATE PACKAGE/BODY on nested END.
                    if (!inCreateUnit && beginDepth == 0) {
                        emitSegment(segments, current, startLine);
                        startLine = line;
                    }
                    continue;
                }
            }

            // SQL*Plus style terminator for CREATE units
            if (c == '/' && isSlashTerminator(content, i, current)) {
                if (inCreateUnit || current.length() > 0) {
                    emitSegment(segments, current, startLine);
                    inCreateUnit = false;
                    beginDepth = 0;
                    startLine = line;
                }
                continue;
            }

            if (c == ';' && !inCreateUnit && beginDepth == 0) {
                current.append(c);
                emitSegment(segments, current, startLine);
                startLine = line;
                continue;
            }

            if (current.length() == 0 && Character.isWhitespace(c)) {
                startLine = line;
                continue;
            }
            current.append(c);
        }
        emitSegment(segments, current, startLine);
        return segments;
    }

    private static void emitSegment(List<Segment> segments, StringBuilder current, int startLine) {
        String text = current.toString().trim();
        if (!text.isEmpty() && !"/".equals(text)) {
            segments.add(new Segment(startLine, text));
        }
        current.setLength(0);
    }

    private static boolean isCreateKeyword(String ahead) {
        return ahead.startsWith("CREATE")
                && (ahead.length() == 6 || !Character.isLetterOrDigit(ahead.charAt(6)));
    }

    private static boolean isPlsqlCreateUnit(String window) {
        return window.contains("PACKAGE")
                || window.matches("(?s).*\\bPROCEDURE\\b.*")
                || window.matches("(?s).*\\bFUNCTION\\b.*")
                || window.matches("(?s).*\\bTRIGGER\\b.*")
                || window.matches("(?s).*\\bTYPE\\b.*");
    }

    private static boolean isKeywordAt(String ahead, String keyword) {
        if (!ahead.startsWith(keyword)) {
            return false;
        }
        return ahead.length() == keyword.length()
                || !Character.isLetterOrDigit(ahead.charAt(keyword.length()));
    }

    private static int indexOfStatementEnd(String content, int from) {
        int j = from;
        while (j < content.length() && content.charAt(j) != ';' && content.charAt(j) != '\n') {
            j++;
        }
        if (j < content.length() && content.charAt(j) == ';') {
            return j;
        }
        return -1;
    }

    /**
     * '/' terminates a CREATE unit when it sits alone (SQL*Plus), not as division operator.
     */
    private static boolean isSlashTerminator(String content, int index, StringBuilder current) {
        // Only when '/' starts a fresh token after newline/whitespace in the current buffer
        // or current buffer already holds a complete unit ending with END...;
        int prev = index - 1;
        while (prev >= 0 && (content.charAt(prev) == ' ' || content.charAt(prev) == '\t')) {
            prev--;
        }
        if (prev >= 0 && content.charAt(prev) != '\n') {
            return false;
        }
        int next = index + 1;
        while (next < content.length() && (content.charAt(next) == ' ' || content.charAt(next) == '\t')) {
            next++;
        }
        if (next < content.length() && content.charAt(next) != '\n' && content.charAt(next) != '\r') {
            return false;
        }
        String soFar = current.toString().trim();
        return !soFar.isEmpty();
    }

    private SqlStatementInfo analyzeSegment(int index, Segment segment) {
        String raw = segment.text;
        String upper = raw.toUpperCase(Locale.ROOT);
        List<String> objects = new ArrayList<>();
        List<String> schemas = new ArrayList<>();
        boolean unsupported = false;
        String unsupportedReason = null;
        boolean missingWhere = false;
        boolean hardSchema = HARD_SCHEMA.matcher(raw).find();
        boolean credential = CREDENTIAL.matcher(raw).find();

        if (DYNAMIC_SQL.matcher(raw).find()) {
            unsupported = true;
            unsupportedReason = "Dynamic SQL (EXECUTE IMMEDIATE/DBMS_SQL) cannot be fully analyzed statically";
        }

        SqlOperationType op = SqlOperationType.UNKNOWN;
        if (upper.contains("PACKAGE BODY") || upper.matches("(?s).*\\bCREATE\\b.*\\bPACKAGE\\s+BODY\\b.*")) {
            op = SqlOperationType.PACKAGE_BODY;
            addObjects(OBJECT_AFTER, raw, objects, schemas);
        } else if (upper.matches("(?s).*\\bCREATE\\b.*\\bPACKAGE\\b.*") && !upper.contains("PACKAGE BODY")) {
            op = SqlOperationType.PACKAGE_SPEC;
            addObjects(OBJECT_AFTER, raw, objects, schemas);
        } else if (startsWithKeyword(upper, "DROP")) {
            String trimmed = upper.stripLeading();
            if (trimmed.matches("(?s)DROP\\s+PACKAGE\\s+BODY\\b.*")) {
                op = SqlOperationType.PACKAGE_BODY;
            } else if (trimmed.matches("(?s)DROP\\s+PACKAGE\\b.*")) {
                op = SqlOperationType.PACKAGE_SPEC;
            } else {
                op = SqlOperationType.DROP;
            }
            addObjects(DROP_TARGET, raw, objects, schemas);
        } else if (startsWithKeyword(upper, "TRUNCATE")) {
            op = SqlOperationType.TRUNCATE;
            addObjects(OBJECT_AFTER, raw, objects, schemas);
        } else if (startsWithKeyword(upper, "DELETE")) {
            op = SqlOperationType.DELETE;
            addObjects(DELETE_TARGET, raw, objects, schemas);
            missingWhere = !WHERE.matcher(raw).find();
        } else if (startsWithKeyword(upper, "UPDATE")) {
            op = SqlOperationType.UPDATE;
            addObjects(UPDATE_TARGET, raw, objects, schemas);
            missingWhere = !WHERE.matcher(raw).find();
        } else if (startsWithKeyword(upper, "ALTER")) {
            op = SqlOperationType.ALTER;
            addObjects(OBJECT_AFTER, raw, objects, schemas);
        } else if (startsWithKeyword(upper, "CREATE")) {
            op = SqlOperationType.CREATE;
            addObjects(OBJECT_AFTER, raw, objects, schemas);
        } else if (GRANT_REVOKE.matcher(upper).find() && startsWithKeyword(upper, "GRANT")) {
            op = SqlOperationType.GRANT;
        } else if (startsWithKeyword(upper, "REVOKE")) {
            op = SqlOperationType.REVOKE;
        } else if (startsWithKeyword(upper, "COMMIT")) {
            op = SqlOperationType.COMMIT;
        } else if (startsWithKeyword(upper, "ROLLBACK")) {
            op = SqlOperationType.ROLLBACK;
        } else if (startsWithKeyword(upper, "MERGE")) {
            op = SqlOperationType.MERGE;
            unsupported = true;
            unsupportedReason = unsupportedReason == null ? "MERGE statements are reported as unsupported for deep analysis" : unsupportedReason;
        } else if (startsWithKeyword(upper, "INSERT")) {
            op = SqlOperationType.INSERT;
            addObjects(OBJECT_AFTER, raw, objects, schemas);
        } else if (startsWithKeyword(upper, "SELECT")) {
            op = SqlOperationType.SELECT;
        } else if (upper.contains("<<") || upper.contains("PRAGMA") || upper.contains("@")) {
            op = SqlOperationType.UNSUPPORTED;
            unsupported = true;
            unsupportedReason = "Ambiguous or unsupported PL/SQL construct";
        }

        if (op == SqlOperationType.UNKNOWN && raw.length() > 20) {
            unsupported = true;
            unsupportedReason = unsupportedReason == null ? "Unrecognized SQL/PLSQL construct" : unsupportedReason;
            op = SqlOperationType.UNSUPPORTED;
        }

        // Deduplicate objects preserving order (keep first schema for each name)
        Set<String> unique = new LinkedHashSet<>();
        List<String> dedupNames = new ArrayList<>();
        List<String> dedupSchemas = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            String name = objects.get(i).toUpperCase(Locale.ROOT);
            if (unique.add(name)) {
                dedupNames.add(name);
                String schema = i < schemas.size() ? schemas.get(i) : null;
                dedupSchemas.add(schema == null ? null : schema.toUpperCase(Locale.ROOT));
            }
        }

        return new SqlStatementInfo(
                index,
                segment.startLine,
                raw,
                op,
                List.copyOf(dedupNames),
                dedupSchemas,
                missingWhere,
                unsupported,
                unsupportedReason,
                hardSchema,
                credential
        );
    }

    private static boolean startsWithKeyword(String upper, String keyword) {
        String trimmed = upper.stripLeading();
        return trimmed.startsWith(keyword)
                && (trimmed.length() == keyword.length() || !Character.isLetterOrDigit(trimmed.charAt(keyword.length())));
    }

    private static void addObjects(Pattern pattern, String raw, List<String> objects, List<String> schemas) {
        Matcher m = pattern.matcher(raw);
        while (m.find()) {
            String schema;
            String name;
            if (m.groupCount() >= 2) {
                schema = m.group(1);
                name = m.group(2);
            } else {
                schema = null;
                name = m.group(1);
            }
            if (name != null && !name.isBlank()) {
                objects.add(name);
                schemas.add(schema);
            }
        }
    }

    private record Segment(int startLine, String text) {
    }
}
