package com.ceudelavanda.lavandaflow.suppliers.infrastructure.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** HTTP request for the supported maintenance fields of a supplier. */
public record UpdateSupplierRequest(
    @NotBlank
    @Size(max = 255)
    String name,
    @Size(max = 255)
    String identifier,
    @Size(max = 255)
    String contact,
    String notes,
    @NotNull
    Boolean active
) {
}
