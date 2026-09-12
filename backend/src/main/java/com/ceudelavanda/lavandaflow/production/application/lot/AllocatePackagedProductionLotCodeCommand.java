package com.ceudelavanda.lavandaflow.production.application.lot;

import java.time.LocalDate;

public record AllocatePackagedProductionLotCodeCommand(LocalDate productionDate) {
}
