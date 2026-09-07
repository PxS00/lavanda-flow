package com.ceudelavanda.lavandaflow.production.application.execution;

import com.ceudelavanda.lavandaflow.production.domain.ProductionLotCodeMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Input for registering one already-completed internal production operation. */
public record RegisterProductionCommand(
    UUID formulaId,
    BigDecimal outputQuantity,
    List<ProductionSourceAllocationCommand> sourceAllocations,
    LocalDate productionDate,
    LocalDate outputReceivedAt,
    LocalDate outputExpiresAt,
    ProductionLotCodeMode lotCodeMode,
    String manualLotCode
) {

    public RegisterProductionCommand {
        sourceAllocations = sourceAllocations == null ? null : List.copyOf(sourceAllocations);
    }
}
