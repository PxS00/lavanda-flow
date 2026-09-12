package com.ceudelavanda.lavandaflow.catalog;

/**
 * Public catalog write contract for initial inventory registration.
 * Catalog policy owns item validation; stock and batches remain with the caller.
 */
public interface InventoryItemRegistration {

    /**
     * Creates one active ESSENCE item measured in MILLILITER with no optional metadata.
     * The catalog registration policy remains authoritative and joins an existing caller transaction.
     */
    InventoryItemSnapshot registerEssence(String name);

    /** Creates a finished product through catalog policy, joining the caller's transaction. */
    InventoryItemSnapshot registerFinishedProduct(FinishedProductRegistration registration);
}
