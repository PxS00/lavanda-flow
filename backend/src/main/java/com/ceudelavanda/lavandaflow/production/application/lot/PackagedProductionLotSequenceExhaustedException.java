package com.ceudelavanda.lavandaflow.production.application.lot;

import com.ceudelavanda.lavandaflow.shared.error.DomainException;
import com.ceudelavanda.lavandaflow.shared.error.ErrorType;

/** Raised when the monthly global packaged-output sequence has already allocated 999. */
public final class PackagedProductionLotSequenceExhaustedException extends DomainException {

    public PackagedProductionLotSequenceExhaustedException() {
        super(
            "PACKAGED_PRODUCTION_LOT_SEQUENCE_EXHAUSTED",
            "Packaged production lot sequence is exhausted for this month and year",
            ErrorType.BUSINESS_RULE
        );
    }
}
