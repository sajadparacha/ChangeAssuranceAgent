package com.company.changeassurance.adapter.in.web;

import java.time.Instant;
import java.util.List;

/**
 * Standard API error model for future REST endpoints (Phase 3).
 * Controllers must never return stack traces to clients.
 */
public record ApiError(
        String correlationId,
        String errorCode,
        String message,
        Instant timestamp,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }
}
