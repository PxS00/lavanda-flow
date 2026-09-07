package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Embeddable
@NoArgsConstructor
class ProductionConsumptionJpaValue {

    @Column(name = "source_batch_id", nullable = false)
    private UUID sourceBatchId;

    @Column(name = "source_inventory_item_id", nullable = false)
    private UUID sourceInventoryItemId;

    @Column(name = "movement_id", nullable = false)
    private UUID movementId;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    ProductionConsumptionJpaValue(
        UUID sourceBatchId,
        UUID sourceInventoryItemId,
        UUID movementId,
        BigDecimal quantity
    ) {
        this.sourceBatchId = sourceBatchId;
        this.sourceInventoryItemId = sourceInventoryItemId;
        this.movementId = movementId;
        this.quantity = quantity;
    }
}
