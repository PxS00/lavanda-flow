package com.ceudelavanda.lavandaflow.shared.security;

import com.ceudelavanda.lavandaflow.shared.error.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
class SecurityErrorWriter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    ApiErrorResponse response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        return new ApiErrorResponse(
            Instant.now(clock),
            status.value(),
            status.getReasonPhrase(),
            code,
            message,
            request.getRequestURI(),
            null
        );
    }

    void write(
        HttpServletRequest request,
        HttpServletResponse response,
        HttpStatus status,
        String code,
        String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getOutputStream(),
            response(status, code, message, request)
        );
    }
}
