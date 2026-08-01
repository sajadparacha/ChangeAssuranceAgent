package com.company.changeassurance.domain.model;

public enum ChangeType {
    DATABASE_SCHEMA,
    PLSQL,
    DATA_CHANGE,
    APPLICATION_CODE,
    CONFIGURATION,
    API_CONTRACT,
    INFRASTRUCTURE,
    SECURITY,
    MIXED,
    UNKNOWN
}
