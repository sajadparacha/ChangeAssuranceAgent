package com.company.changeassurance.domain.sql;

import java.util.List;
import java.util.Objects;

public record SqlStatementInfo(
        int index,
        int startLine,
        String rawText,
        SqlOperationType operationType,
        List<String> objectNames,
        boolean missingWhere,
        boolean unsupported,
        String unsupportedReason,
        boolean hardCodedSchema,
        boolean potentialCredential
) {
    public SqlStatementInfo {
        Objects.requireNonNull(operationType, "operationType");
        objectNames = List.copyOf(Objects.requireNonNullElse(objectNames, List.of()));
        rawText = rawText == null ? "" : rawText;
    }
}
