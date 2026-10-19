package com.ceudelavanda.lavandaflow.sales.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Exact operator request; both decisions must explicitly be true before inventory may be credited. */
@io.swagger.v3.oas.annotations.media.Schema(description = "All fields are required. Original confirmed line and allocated batch UUIDs; exact positive quantity fitting NUMERIC(19,6) in the original unit; trimmed reason with 1–255 characters; inspected and accepted must explicitly be true.")
public record RegisterSaleStockCorrection(UUID lineId, UUID batchId, BigDecimal quantity,
                                          String reason, Boolean inspected, Boolean accepted) {}
