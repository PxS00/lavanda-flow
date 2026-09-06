package com.ceudelavanda.lavandaflow.catalog;

/**
 * Public catalog write contract for creating the fixed essence item required by the initial
 * inventory snapshot.
 */
public interface InventoryItemRegistration {

    /**
     * Creates one active ESSENCE item measured in MILLILITER with no optional metadata.
     * The catalog registration policy remains authoritative and joins an existing caller transaction.
     */
    InventoryItemSnapshot registerEssence(String name);
}
