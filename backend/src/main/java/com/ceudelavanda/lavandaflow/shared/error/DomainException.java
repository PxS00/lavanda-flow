package com.ceudelavanda.lavandaflow.shared.error;

import lombok.Getter;

@Getter
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final ErrorType errorType;

    protected DomainException(
        String code,
        String message,
        ErrorType errorType
    ) {
        super(message);
        this.code = code;
        this.errorType = errorType;
    }
}
