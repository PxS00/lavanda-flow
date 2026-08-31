package com.ceudelavanda.lavandaflow.suppliers;

import java.util.Optional;
import java.util.UUID;

/**
 * Public suppliers query contract for consumers that need to validate a supplier
 * without accessing supplier persistence internals.
 */
public interface SupplierLookup {

    Optional<SupplierSnapshot> findById(UUID supplierId);
}
