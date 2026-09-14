package com.ceudelavanda.lavandaflow.catalog;

/** Catalog-owned input for registering a distinct finished-product presentation. */
public record FinishedProductRegistration(
    String name, String description, UnitOfMeasure unitOfMeasure,
    String essenceReference, String productionTypeCode, ProductGender gender
) {
}
