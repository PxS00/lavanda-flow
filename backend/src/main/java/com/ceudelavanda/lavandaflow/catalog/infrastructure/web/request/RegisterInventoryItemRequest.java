package com.ceudelavanda.lavandaflow.catalog.infrastructure.web.request;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record RegisterInventoryItemRequest(
    @NotBlank
    @Size(max = 255)
    String name,
    String description,
    @NotNull
    Category category,
    @NotNull
    UnitOfMeasure unitOfMeasure,
    @Pattern(regexp = "(?:00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})")
    String essenceReference,
    @Pattern(regexp = "[A-Z]{3}")
    String productionTypeCode
) {
}
