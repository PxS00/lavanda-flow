package com.ceudelavanda.lavandaflow.customers.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.customers.application.CustomerResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Customer contact, stable identity, state and audit timestamps")
public record CustomerResponse(UUID id,
                               @Schema(maxLength = 160) String name,
                               @Schema(maxLength = 16, pattern = "\\+?[0-9]{7,15}") String phone,
                               @Schema(maxLength = 254, format = "email") String email, boolean active,
                               Instant createdAt, Instant updatedAt) {
    public static CustomerResponse from(CustomerResult result) {
        return new CustomerResponse(result.id(), result.name(), result.phone(), result.email(),
            result.active(), result.createdAt(), result.updatedAt());
    }
}
