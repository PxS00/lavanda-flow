package com.ceudelavanda.lavandaflow.sales.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.sales.domain.SaleStockCorrection;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sale_stock_correction")
@NoArgsConstructor
class SaleStockCorrectionJpaEntity {
    @Embeddable
    record Key(UUID saleId, UUID id) implements Serializable {}
    @EmbeddedId Key key;
    @Column(nullable = false) UUID lineId;
    @Column(nullable = false) UUID batchId;
    @Column(nullable = false, precision = 19, scale = 6) BigDecimal quantity;
    @Column(nullable = false, length = 255) String reason;
    @Column(nullable = false) boolean inspected;
    @Column(nullable = false) boolean accepted;
    @Column(nullable = false, length = 100) String actor;
    @Column(nullable = false) Instant occurredAt;
    @Column(nullable = false) UUID movementId;

    SaleStockCorrectionJpaEntity(SaleStockCorrection c) {
        key = new Key(c.saleId(), c.id()); lineId = c.lineId(); batchId = c.batchId(); quantity = c.quantity();
        reason = c.reason(); inspected = c.inspected(); accepted = c.accepted(); actor = c.actor();
        occurredAt = c.occurredAt(); movementId = c.movementId();
    }
    SaleStockCorrection toDomain() {
        return new SaleStockCorrection(key.id(), key.saleId(), lineId, batchId, quantity, reason,
            inspected, accepted, actor, occurredAt, movementId);
    }
}
