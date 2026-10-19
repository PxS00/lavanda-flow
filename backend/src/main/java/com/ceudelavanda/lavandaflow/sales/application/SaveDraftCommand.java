package com.ceudelavanda.lavandaflow.sales.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Complete draft replacement. Null line ID allocates an ID, or retains the existing item line ID.
 * An explicit ID must already belong to the same item in this order. Customer is optional. */
public record SaveDraftCommand(UUID customerId, List<Line> lines) {
    public record Line(UUID id, UUID itemId, BigDecimal quantity, BigDecimal unitPrice) {}
}
