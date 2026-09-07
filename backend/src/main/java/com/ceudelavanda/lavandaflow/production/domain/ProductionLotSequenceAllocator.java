package com.ceudelavanda.lavandaflow.production.domain;

import java.util.OptionalInt;

/**
 * Allocates the next internal production lot sequence for one monthly prefix.
 *
 * <p>An empty result means that the prefix has reached its maximum sequence.
 * Implementations must participate in the caller's transaction.</p>
 */
public interface ProductionLotSequenceAllocator {

    OptionalInt allocate(
        String productionTypeCode,
        String essenceReference,
        int productionYear,
        int productionMonth
    );
}
