package com.ceudelavanda.lavandaflow.catalog;

import java.util.Optional;
import java.util.UUID;

/** Public catalog lookup contract for production metadata. */
public interface ProductionItemReferenceLookup {

    Optional<ProductionItemReference> findByInventoryItemId(UUID inventoryItemId);
}
