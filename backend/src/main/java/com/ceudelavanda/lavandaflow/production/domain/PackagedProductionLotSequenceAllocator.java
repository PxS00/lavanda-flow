package com.ceudelavanda.lavandaflow.production.domain;

import java.util.OptionalInt;

/** Allocates the next packaged finished-product sequence for one calendar month/year. */
public interface PackagedProductionLotSequenceAllocator {

    OptionalInt allocate(int productionYear, int productionMonth);
}
