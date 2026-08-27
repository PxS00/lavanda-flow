package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

interface SpringDataBatchRepository extends JpaRepository<BatchJpaEntity, UUID> {

    List<BatchJpaEntity> findByInventoryItemId(UUID inventoryItemId);

    List<BatchJpaEntity> findByExpiresAtLessThanEqualAndCurrentQuantityGreaterThan(
        LocalDate expiresAt,
        BigDecimal currentQuantity
    );

    @Query("""
        select new com.ceudelavanda.lavandaflow.inventory.application.AvailableStockBalance(
            batch.inventoryItemId, sum(batch.currentQuantity)
        )
        from BatchJpaEntity batch
        where batch.inventoryItemId in :inventoryItemIds
          and batch.currentQuantity > 0
          and (batch.expiresAt is null or batch.expiresAt > :asOfDate)
        group by batch.inventoryItemId
        """)
    List<com.ceudelavanda.lavandaflow.inventory.application.AvailableStockBalance> findAvailableStockBalances(
        @Param("inventoryItemIds") Collection<UUID> inventoryItemIds,
        @Param("asOfDate") LocalDate asOfDate
    );
}
