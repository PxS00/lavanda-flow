package com.ceudelavanda.lavandaflow.suppliers.application;

import com.ceudelavanda.lavandaflow.suppliers.domain.Supplier;

import java.util.UUID;

/** Stable application read model for one supplier. */
public record SupplierResult(
    UUID id,
    String name,
    String identifier,
    String contact,
    String notes,
    boolean active
) {
    public static SupplierResult from(Supplier supplier) {
        return new SupplierResult(
            supplier.getId(),
            supplier.getName(),
            supplier.getIdentifier(),
            supplier.getContact(),
            supplier.getNotes(),
            supplier.isActive()
        );
    }
}
