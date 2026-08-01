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
                    + "(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?(?:\"?[A-Z0-9_$#]+\"?\\.)?\"?([A-Z0-9_$#]+)\"?");
    private static final Pattern UPDATE_TARGET = Pattern.compile(
            "(?i)\\bUPDATE\\s+(?:\"?[A-Z0-9_$#]+\"?\\.)?\"?([A-Z0-9_$#]+)\"?");
    private static final Pattern DELETE_TARGET = Pattern.compile(
            "(?i)\\bDELETE\\s+FROM\\s+(?:\"?[A-Z0-9_$#]+\"?\\.)?\"?([A-Z0-9_$#]+)\"?");
    private static final Pattern DROP_TARGET = Pattern.compile(
            "(?i)\\bDROP\\s+(?:TABLE|INDEX|VIEW|SEQUENCE|PACKAGE(?:\\s+BODY)?|PROCEDURE|FUNCTION|TRIGGER)\\s+"
                    + "(?:IF\\s+EXISTS\\s+)?(?:\"?[A-Z0-9_$#]+\"?\\.)?\"?([A-Z0-9_$#]+)\"?");
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
        int plsqlDepth = 0;
        boolean inPlsqlBlock = false;

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

            // Detect CREATE OR REPLACE PACKAGE / BEGIN..END blocks heuristically
            String ahead = content.substring(i, Math.min(content.length(), i + 32)).toUpperCase(Locale.ROOT);
            if (!inPlsqlBlock && (ahead.startsWith("CREATE ") || ahead.startsWith("CREATE\t") || ahead.startsWith("CREATE\n"))) {
                String window = content.substring(i, Math.min(content.length(), i + 120)).toUpperCase(Locale.ROOT);
                if (window.contains("PACKAGE") || window.contains("PROCEDURE") || window.contains("FUNCTION") || window.contains("TRIGGER") || window.contains("TYPE ")) {
                    inPlsqlBlock = true;
                    plsqlDepth = 0;
                }
            }
            if (inPlsqlBlock) {
                if (ahead.startsWith("BEGIN") && (ahead.length() == 5 || !Character.isLetterOrDigit(ahead.charAt(5)))) {
                    plsqlDepth++;
                }
                if (ahead.startsWith("END") && (ahead.length() == 3 || !Character.isLetterOrDigit(ahead.charAt(3)))) {
                    // look for END; 
                    int j = i;
                    while (j < content.length() && content.charAt(j) != ';' && content.charAt(j) != '\n') {
                        j++;
                    }
                    if (j < content.length() && content.charAt(j) == ';') {
                        plsqlDepth = Math.max(0, plsqlDepth - 1);
                        current.append(content, i, j + 1);
                        i = j;
                        if (plsqlDepth == 0) {
                            // check if this closes package with /
                            inPlsqlBlock = false;
                            String text = current.toString().trim();
                            if (!text.isEmpty()) {
                                segments.add(new Segment(startLine, text));
                            }
                            current.setLength(0);
                            startLine = line;
                            // optional slash terminator
                            continue;
                        }
                        continue;
                    }
                }
            }

            if (c == ';' && !inPlsqlBlock) {
                current.append(c);
                String text = current.toString().trim();
                if (!text.isEmpty() && !"/".equals(text)) {
                    segments.add(new Segment(startLine, text));
                }
                current.setLength(0);
                startLine = line;
                continue;
            }

            if (c == '/' && current.toString().trim().isEmpty() && inPlsqlBlock == false) {
                // standalone slash — ignore
                continue;
            }

            if (current.length() == 0 && Character.isWhitespace(c)) {
                startLine = line;
                continue;
            }
            current.append(c);
        }
        String trailing = current.toString().trim();
        if (!trailing.isEmpty()) {
            segments.add(new Segment(startLine, trailing));
        }
        return segments;
    }

    private SqlStatementInfo analyzeSegment(int index, Segment segment) {
        String raw = segment.text;
        String upper = raw.toUpperCase(Locale.ROOT);
        List<String> objects = new ArrayList<>();
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
            addObjects(OBJECT_AFTER, raw, objects);
        } else if (upper.matches("(?s).*\\bCREATE\\b.*\\bPACKAGE\\b.*") && !upper.contains("PACKAGE BODY")) {
            op = SqlOperationType.PACKAGE_SPEC;
            addObjects(OBJECT_AFTER, raw, objects);
        } else if (startsWithKeyword(upper, "DROP")) {
            op = SqlOperationType.DROP;
            addObjects(DROP_TARGET, raw, objects);
        } else if (startsWithKeyword(upper, "TRUNCATE")) {
            op = SqlOperationType.TRUNCATE;
            addObjects(OBJECT_AFTER, raw, objects);
        } else if (startsWithKeyword(upper, "DELETE")) {
            op = SqlOperationType.DELETE;
            addObjects(DELETE_TARGET, raw, objects);
            missingWhere = !WHERE.matcher(raw).find();
        } else if (startsWithKeyword(upper, "UPDATE")) {
            op = SqlOperationType.UPDATE;
            addObjects(UPDATE_TARGET, raw, objects);
            missingWhere = !WHERE.matcher(raw).find();
        } else if (startsWithKeyword(upper, "ALTER")) {
            op = SqlOperationType.ALTER;
            addObjects(OBJECT_AFTER, raw, objects);
        } else if (startsWithKeyword(upper, "CREATE")) {
            op = SqlOperationType.CREATE;
            addObjects(OBJECT_AFTER, raw, objects);
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
            addObjects(OBJECT_AFTER, raw, objects);
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

        // Deduplicate objects preserving order
        Set<String> unique = new LinkedHashSet<>();
        for (String o : objects) {
            unique.add(o.toUpperCase(Locale.ROOT));
        }

        return new SqlStatementInfo(
                index,
                segment.startLine,
                raw,
                op,
                List.copyOf(unique),
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

    private static void addObjects(Pattern pattern, String raw, List<String> objects) {
        Matcher m = pattern.matcher(raw);
        while (m.find()) {
            String name = m.group(1);
            if (name != null && !name.isBlank()) {
                objects.add(name);
            }
        }
    }

    private record Segment(int startLine, String text) {
    }
}
