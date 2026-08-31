package com.ceudelavanda.lavandaflow.suppliers;

import java.util.UUID;

/** Public supplier data required by other application modules. */
public record SupplierSnapshot(UUID id, String name, boolean active) {
}
