package com.ceudelavanda.lavandaflow.suppliers.application;

import java.util.UUID;

/** Supported maintenance fields for an existing supplier. */
public record UpdateSupplierCommand(
    UUID supplierId,
    String name,
    String identifier,
    String contact,
    String notes,
    boolean active
) {
}
