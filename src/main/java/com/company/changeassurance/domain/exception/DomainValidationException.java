package com.company.changeassurance.domain.exception;

public class DomainValidationException extends DomainException {

    public DomainValidationException(String message) {
        super("DOMAIN_VALIDATION", message);
    }
}
