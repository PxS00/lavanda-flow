package com.ceudelavanda.lavandaflow.suppliers.application;

import java.util.Optional;
import java.util.UUID;

/** Read port for supplier consultation. */
public interface SupplierQuery {

    Optional<SupplierResult> findById(UUID supplierId);

    SupplierPage search(SupplierSearchQuery query);
}
