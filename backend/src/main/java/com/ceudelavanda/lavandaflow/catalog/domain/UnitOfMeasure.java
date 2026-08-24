package com.ceudelavanda.lavandaflow.catalog.domain;
/**
 * Defines the unit used to control the quantity of an inventory item.
 *
 * <p>Quantities associated with these units must use exact decimal
 * representations such as {@link java.math.BigDecimal}. Floating-point
 * types must not be used for inventory quantities.</p>
 */
public enum UnitOfMeasure {

    MILLILITER,
    LITER,
    GRAM,
    KILOGRAM,
    UNIT
}
