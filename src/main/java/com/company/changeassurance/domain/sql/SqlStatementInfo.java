package com.company.changeassurance.domain.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record SqlStatementInfo(
        int index,
        int startLine,
        String rawText,
        SqlOperationType operationType,
        List<String> objectNames,
        List<String> objectSchemas,
        boolean missingWhere,
        boolean unsupported,
        String unsupportedReason,
        boolean hardCodedSchema,
        boolean potentialCredential
) {
    public SqlStatementInfo {
        Objects.requireNonNull(operationType, "operationType");
        objectNames = List.copyOf(Objects.requireNonNullElse(objectNames, List.of()));
        objectSchemas = normalizeSchemas(objectSchemas, objectNames.size());
        rawText = rawText == null ? "" : rawText;
    }

    /**
     * Backward-compatible constructor without per-object schemas.
     */
    public SqlStatementInfo(
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
        this(
                index,
                startLine,
                rawText,
                operationType,
                objectNames,
                List.of(),
                missingWhere,
                unsupported,
                unsupportedReason,
                hardCodedSchema,
                potentialCredential
        );
    }

    private static List<String> normalizeSchemas(List<String> schemas, int nameCount) {
        List<String> source = schemas == null ? List.of() : schemas;
        List<String> normalized = new ArrayList<>(nameCount);
        for (int i = 0; i < nameCount; i++) {
            String schema = i < source.size() ? source.get(i) : null;
            if (schema != null && schema.isBlank()) {
                schema = null;
            }
            normalized.add(schema);
        }
        // Allow null schema entries (unqualified objects); List.copyOf forbids nulls.
        return Collections.unmodifiableList(normalized);
    }
}
