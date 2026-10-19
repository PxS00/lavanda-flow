package com.ceudelavanda.lavandaflow.sales.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable authorization and correlation for one inspected, accepted return; movement owns stock history. */
public record SaleStockCorrection(UUID id, UUID saleId, UUID lineId, UUID batchId, BigDecimal quantity,
                                  String reason, boolean inspected, boolean accepted, String actor,
                                  Instant occurredAt, UUID movementId) {}
