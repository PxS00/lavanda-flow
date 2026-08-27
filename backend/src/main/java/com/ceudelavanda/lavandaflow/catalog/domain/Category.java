package com.ceudelavanda.lavandaflow.catalog.domain;
/**
 * Classifies an inventory item according to its primary catalog purpose.
 *
 * <p>Categories are intentionally descriptive and must not be used as a
 * substitute for explicit business rules.</p>
 */
public enum Category {

    ESSENCE,
    CHEMICAL_INPUT,
    BASE,
    ALCOHOL,
    COLORANT,
    FIXATIVE,
    BOTTLE,
    VALVE,
    CAP,
    LABEL,
    PACKAGING,
    OTHER
}
