package com.company.changeassurance.domain.exception;

/**
 * Raised when a model gateway cannot serve an AI task (disabled or unreachable).
 * Callers must fall back without failing the whole review solely for this reason.
 */
public class AiUnavailableException extends DomainException {

    public AiUnavailableException(String message) {
        super("AI_UNAVAILABLE", message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super("AI_UNAVAILABLE", message, cause);
    }
}
