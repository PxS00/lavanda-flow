package com.ceudelavanda.lavandaflow.production.application.formula;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

import java.util.Map;
import java.util.UUID;

public final class ProductionFormulaNotFoundException extends DomainException {

    public ProductionFormulaNotFoundException(UUID formulaId) {
        super(
            "PRODUCTION_FORMULA_NOT_FOUND",
            "Production formula was not found",
            ErrorType.NOT_FOUND,
            Map.of("formulaId", String.valueOf(formulaId))
        );
    }
}
