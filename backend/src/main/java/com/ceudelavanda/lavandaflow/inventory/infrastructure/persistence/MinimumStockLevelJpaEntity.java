package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "inventory_minimum_stock_level")
@NoArgsConstructor
class MinimumStockLevelJpaEntity {

    @Id
    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;

    @Column(name = "minimum_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal minimumQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    MinimumStockLevelJpaEntity(UUID inventoryItemId, BigDecimal minimumQuantity) {
        this.inventoryItemId = inventoryItemId;
        this.minimumQuantity = minimumQuantity;
    }

    void changeMinimumQuantity(BigDecimal minimumQuantity) {
        this.minimumQuantity = minimumQuantity;
    }

    @PrePersist
    void setCreationTimestamps() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void setUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}
