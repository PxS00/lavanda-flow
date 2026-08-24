package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "inventory_batch")
@NoArgsConstructor
class BatchJpaEntity {

    @Id
    private UUID id;

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "lot_code")
    private String lotCode;

    @Column(name = "initial_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal initialQuantity;

    @Column(name = "current_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal currentQuantity;

    @Column(name = "received_at", nullable = false)
    private LocalDate receivedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    BatchJpaEntity(
        UUID id,
        UUID inventoryItemId,
        UUID supplierId,
        String lotCode,
        BigDecimal initialQuantity,
        BigDecimal currentQuantity,
        LocalDate receivedAt,
        LocalDate expiresAt
    ) {
        this.id = id;
        this.inventoryItemId = inventoryItemId;
        this.supplierId = supplierId;
        this.lotCode = lotCode;
        this.initialQuantity = initialQuantity;
        this.currentQuantity = currentQuantity;
        this.receivedAt = receivedAt;
        this.expiresAt = expiresAt;
    }
}
