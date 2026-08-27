package com.ceudelavanda.lavandaflow.suppliers.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for suppliers managed by the suppliers module.
 */
public interface SupplierRepository {

    Supplier save(Supplier supplier);

    Optional<Supplier> findById(UUID id);
}
