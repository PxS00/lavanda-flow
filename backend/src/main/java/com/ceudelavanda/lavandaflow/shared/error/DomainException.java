package com.ceudelavanda.lavandaflow.shared.error;

import lombok.Getter;

import java.util.Map;

@Getter
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final ErrorType errorType;
    private final Map<String, String> details;

    protected DomainException(
        String code,
        String message,
        ErrorType errorType
    ) {
        this(code, message, errorType, null);
    }

    protected DomainException(
        String code,
        String message,
        ErrorType errorType,
        Map<String, String> details
    ) {
        super(message);
        this.code = code;
        this.errorType = errorType;
        this.details = details == null ? null : Map.copyOf(details);
    }
}
