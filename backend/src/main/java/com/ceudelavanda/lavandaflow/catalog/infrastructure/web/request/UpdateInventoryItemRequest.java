package com.ceudelavanda.lavandaflow.catalog.infrastructure.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HTTP request for the restricted maintenance surface of an inventory item. */
public record UpdateInventoryItemRequest(
    @NotBlank @Size(max = 255) String name,
    String description,
    @NotNull Boolean active,
    @Pattern(regexp = "(?:00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})") String essenceReference,
    @Pattern(regexp = "[A-Z]{3}") String productionTypeCode
) {
}
