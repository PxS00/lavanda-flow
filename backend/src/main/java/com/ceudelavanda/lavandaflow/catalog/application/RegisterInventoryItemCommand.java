package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;

/** Input for registering a new inventory catalog item. */
public record RegisterInventoryItemCommand(
    String name,
    String description,
    Category category,
    UnitOfMeasure unitOfMeasure
) {
}
