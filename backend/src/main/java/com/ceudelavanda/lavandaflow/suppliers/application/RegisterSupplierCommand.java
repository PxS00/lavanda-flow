package com.ceudelavanda.lavandaflow.suppliers.application;

/** Input for registering a new supplier. */
public record RegisterSupplierCommand(
    String name,
    String identifier,
    String contact,
    String notes
) {
}
