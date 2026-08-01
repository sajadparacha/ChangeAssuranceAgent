package com.company.changeassurance.domain.sql;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record SqlParseResult(
        String filename,
        boolean fullySupported,
        List<SqlStatementInfo> statements,
        List<String> unsupportedConstructs,
        List<String> preprocessingNotes
) {
    public SqlParseResult {
        Objects.requireNonNull(filename, "filename");
        statements = List.copyOf(Objects.requireNonNullElse(statements, List.of()));
        unsupportedConstructs = List.copyOf(Objects.requireNonNullElse(unsupportedConstructs, List.of()));
        preprocessingNotes = List.copyOf(Objects.requireNonNullElse(preprocessingNotes, List.of()));
    }

    public Set<String> affectedObjectNames() {
        Set<String> names = new LinkedHashSet<>();
        for (SqlStatementInfo s : statements) {
            names.addAll(s.objectNames());
        }
        return names;
    }

    public List<SqlOperationType> detectedOperations() {
        return statements.stream().map(SqlStatementInfo::operationType).distinct().toList();
    }
}
