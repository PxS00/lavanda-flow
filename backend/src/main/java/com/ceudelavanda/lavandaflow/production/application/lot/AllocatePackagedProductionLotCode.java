package com.ceudelavanda.lavandaflow.production.application.lot;

import com.ceudelavanda.lavandaflow.production.domain.PackagedProductionLotSequenceAllocator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Allocates the definitive monthly lot code for a packaged finished-product output. */
@Service
@RequiredArgsConstructor
public class AllocatePackagedProductionLotCode {

    private final PackagedProductionLotSequenceAllocator packagedProductionLotSequenceAllocator;

    @Transactional(propagation = Propagation.MANDATORY)
    public PackagedProductionLotCode execute(AllocatePackagedProductionLotCodeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var productionDate = Objects.requireNonNull(command.productionDate(), "productionDate must not be null");
        var sequence = packagedProductionLotSequenceAllocator.allocate(
            productionDate.getYear(),
            productionDate.getMonthValue()
        ).orElseThrow(PackagedProductionLotSequenceExhaustedException::new);

        return new PackagedProductionLotCode(
            "%03d-%02d-%04d".formatted(
                sequence,
                productionDate.getMonthValue(),
                productionDate.getYear()
            )
        );
    }
}
