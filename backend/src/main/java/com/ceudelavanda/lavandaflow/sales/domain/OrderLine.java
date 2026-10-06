package com.ceudelavanda.lavandaflow.sales.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Stable draft identity and exact entered values; display snapshots belong to confirmation. */
public record OrderLine(UUID id, UUID itemId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {}
