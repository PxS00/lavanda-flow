package com.ceudelavanda.lavandaflow.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
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
        var status = toHttpStatus(exception.getErrorType());

        var response = new ApiErrorResponse(
            Instant.now(clock),
            status.value(),
            status.getReasonPhrase(),
            exception.getCode(),
            exception.getMessage(),
            request.getRequestURI(),
            exception.getDetails()
        );

        return ResponseEntity
            .status(status)
            .body(response);
    }

    private static HttpStatus toHttpStatus(ErrorType errorType) {
        return switch (errorType) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case CONFLICT -> HttpStatus.CONFLICT;
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        var details = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                GlobalExceptionHandler::getValidationMessage,
                (first, second) -> first
            ));

        return badRequest(
            "VALIDATION_ERROR",
            "Request validation failed",
            request,
            details
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        return badRequest(
            "INVALID_REQUEST_PARAMETER",
            "Request parameter has an invalid value",
            request,
            Map.of(exception.getName(), "Invalid value")
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        return badRequest(
            "MALFORMED_REQUEST_BODY",
            "Request body is malformed or unreadable",
            request,
            null
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
        MissingServletRequestParameterException exception,
        HttpServletRequest request
    ) {
        return badRequest(
            "MISSING_REQUEST_PARAMETER",
            "Required request parameter is missing",
            request,
            Map.of(exception.getParameterName(), "Required parameter")
        );
    }

    private ResponseEntity<ApiErrorResponse> badRequest(
        String code,
        String message,
        HttpServletRequest request,
        Map<String, String> details
    ) {
        var status = HttpStatus.BAD_REQUEST;
        var response = new ApiErrorResponse(
            Instant.now(clock),
            status.value(),
            status.getReasonPhrase(),
            code,
            message,
            request.getRequestURI(),
            details
        );

        return ResponseEntity
            .status(status)
            .body(response);
    }

    private static String getValidationMessage(
        DefaultMessageSourceResolvable error
    ) {
        var message = error.getDefaultMessage();
        return message != null ? message : "Invalid value";
    }
}
