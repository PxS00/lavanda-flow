package com.ceudelavanda.lavandaflow.catalog.infrastructure.web.request;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterInventoryItemRequest(
    @NotBlank
    @Size(max = 255)
    String name,
    String description,
    @NotNull
    Category category,
    @NotNull
    UnitOfMeasure unitOfMeasure
) {
}
