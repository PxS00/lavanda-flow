package com.ceudelavanda.lavandaflow.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(
        DomainException exception,
        HttpServletRequest request
    ) {
        var status = exception.getStatus();

        var response = new ApiErrorResponse(
            Instant.now(clock),
            status.value(),
            status.getReasonPhrase(),
            exception.getCode(),
            exception.getMessage(),
            request.getRequestURI(),
            null
        );

        return ResponseEntity
            .status(status)
            .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        var status = HttpStatus.BAD_REQUEST;

        var details = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                fieldError -> fieldError.getField(),
                fieldError -> fieldError.getDefaultMessage(),
                (first, second) -> first
            ));

        var response = new ApiErrorResponse(
            Instant.now(clock),
            status.value(),
            status.getReasonPhrase(),
            "VALIDATION_ERROR",
            "Request validation failed",
            request.getRequestURI(),
            details
        );

        return ResponseEntity
            .status(status)
            .body(response);
    }
}
