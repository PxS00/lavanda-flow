package com.ceudelavanda.lavandaflow.production.application.lot;

import com.ceudelavanda.lavandaflow.catalog.ProductionItemReferenceLookup;
import com.ceudelavanda.lavandaflow.production.domain.ProductionLotSequenceAllocator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Allocates the definitive generated lot code for an internal production output.
 *
 * <p>The caller must provide the encompassing production transaction. This use case
 * updates only the production-owned sequence state and does not reserve a code.</p>
 */
@Service
@RequiredArgsConstructor
public class AllocateInternalProductionLotCode {

    private static final String NO_ESSENCE_REFERENCE = "000";

    private final ProductionItemReferenceLookup productionItemReferenceLookup;
    private final ProductionLotSequenceAllocator productionLotSequenceAllocator;

    /**
     * Allocates one lot code in the transaction that will persist the production execution.
     *
     * @throws ProductionLotCodeCatalogItemNotFoundException if the output item does not exist
     * @throws MissingProductionTypeCodeException if the output item has no production type code
     * @throws ProductionLotSequenceExhaustedException if the scoped sequence has reached 999
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public InternalProductionLotCode execute(AllocateInternalProductionLotCodeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var outputInventoryItemId = Objects.requireNonNull(
            command.outputInventoryItemId(), "outputInventoryItemId must not be null"
        );
        var productionDate = Objects.requireNonNull(command.productionDate(), "productionDate must not be null");
        var outputItem = productionItemReferenceLookup.findByInventoryItemId(outputInventoryItemId)
            .orElseThrow(() -> new ProductionLotCodeCatalogItemNotFoundException(outputInventoryItemId));
        var productionTypeCode = outputItem.productionTypeCode();

        if (productionTypeCode == null) {
            throw new MissingProductionTypeCodeException(outputInventoryItemId);
        }

        var essenceReference = outputItem.essenceReference() == null
            ? NO_ESSENCE_REFERENCE
            : outputItem.essenceReference();
        var sequence = productionLotSequenceAllocator.allocate(
            productionTypeCode,
            essenceReference,
            productionDate.getYear(),
            productionDate.getMonthValue()
        ).orElseThrow(ProductionLotSequenceExhaustedException::new);

        return new InternalProductionLotCode(
            "%s-%s-%03d-%02d-%04d".formatted(
                productionTypeCode,
                essenceReference,
                sequence,
                productionDate.getMonthValue(),
                productionDate.getYear()
            )
        );
    }
}
