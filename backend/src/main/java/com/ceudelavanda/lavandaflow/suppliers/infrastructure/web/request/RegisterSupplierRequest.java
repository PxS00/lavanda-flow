package com.ceudelavanda.lavandaflow.suppliers.infrastructure.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterSupplierRequest(
    @NotBlank
    @Size(max = 255)
    String name,
    @Size(max = 255)
    String identifier,
    @Size(max = 255)
    String contact,
    String notes
) {
}
