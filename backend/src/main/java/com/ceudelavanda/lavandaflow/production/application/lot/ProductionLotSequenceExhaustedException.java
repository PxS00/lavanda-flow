package com.ceudelavanda.lavandaflow.production.application.lot;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

/** Raised when a monthly internal production lot prefix has already allocated sequence 999. */
public final class ProductionLotSequenceExhaustedException extends DomainException {

    public ProductionLotSequenceExhaustedException() {
        super(
            "PRODUCTION_LOT_SEQUENCE_EXHAUSTED",
            "Internal production lot sequence is exhausted for this production type, essence, month, and year",
            ErrorType.BUSINESS_RULE
        );
    }
}
