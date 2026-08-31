package com.ceudelavanda.lavandaflow.suppliers.infrastructure.web.response;

import com.ceudelavanda.lavandaflow.suppliers.application.SupplierResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Supplier registration data")
public record SupplierResponse(
    UUID id,
    String name,
    String identifier,
    String contact,
    String notes,
    boolean active
) {
    public static SupplierResponse from(SupplierResult result) {
        return new SupplierResponse(
            result.id(),
            result.name(),
            result.identifier(),
            result.contact(),
            result.notes(),
            result.active()
        );
    }
}
