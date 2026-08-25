package com.ceudelavanda.lavandaflow.shared.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    protected DomainException(
        String code,
        String message,
        HttpStatus status
    ) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
