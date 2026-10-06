package com.ceudelavanda.lavandaflow.sales.infrastructure.web;

import com.ceudelavanda.lavandaflow.shared.error.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Clock;
import java.time.Instant;

/** Sales-only transport mapping after the application transaction has rolled back. No automatic retry. */
@RestControllerAdvice(assignableTypes = OrderController.class)
@RequiredArgsConstructor
@Slf4j
class OrderPersistenceExceptionHandler {
    private final Clock clock;

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> lockFailure(PessimisticLockingFailureException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ORDER_LOCK_CONFLICT", "Order operation could not acquire its required locks", request);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<ApiErrorResponse> persistenceFailure(RuntimeException exception, HttpServletRequest request) {
        log.error("Order persistence failed for {} {}", request.getMethod(), request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER_PERSISTENCE_FAILED", "Order operation could not be persisted", request);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(Instant.now(clock), status.value(),
            status.getReasonPhrase(), code, message, request.getRequestURI(), null));
    }
}
