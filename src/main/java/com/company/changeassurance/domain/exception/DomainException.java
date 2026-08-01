package com.company.changeassurance.domain.exception;

/**
 * Base type for all domain exceptions. Free of framework dependencies.
 */
public class DomainException extends RuntimeException {

    private final String errorCode;

    public DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode == null ? "DOMAIN_ERROR" : errorCode;
    }

    public DomainException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode == null ? "DOMAIN_ERROR" : errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
