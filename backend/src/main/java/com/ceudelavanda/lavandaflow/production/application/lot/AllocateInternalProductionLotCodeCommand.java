package com.ceudelavanda.lavandaflow.production.application.lot;

import java.time.LocalDate;
import java.util.UUID;

/** Input required to allocate one definitive generated internal production lot code. */
public record AllocateInternalProductionLotCodeCommand(
    UUID outputInventoryItemId,
    LocalDate productionDate
) {
}
