package com.ceudelavanda.lavandaflow.sales.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Exact immutable references returned by inventory; no cross-module entity relationship. */
public record SaleAllocation(UUID batchId, UUID movementId, BigDecimal quantity) {}
