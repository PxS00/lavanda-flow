package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;
import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

/** Raised when current catalog units no longer match the unit snapshot stored by a formula. */
public final class ProductionFormulaUnitMismatchException extends DomainException {

    public ProductionFormulaUnitMismatchException(
        UUID inventoryItemId,
        UnitOfMeasure formulaUnit,
        UnitOfMeasure catalogUnit
    ) {
        super(
            "PRODUCTION_FORMULA_UNIT_MISMATCH",
            "Current catalog unit does not match the production formula unit",
            ErrorType.BUSINESS_RULE,
            Map.of(
                "inventoryItemId", String.valueOf(inventoryItemId),
                "formulaUnit", formulaUnit.name(),
                "catalogUnit", catalogUnit.name()
            )
        );
    }
}
