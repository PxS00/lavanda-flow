package com.ceudelavanda.lavandaflow.shared.error;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(

    Instant timestamp,
    int status,
    String error,
    String code,
    String message,
    String path,
    Map<String, String> details
) {
}
