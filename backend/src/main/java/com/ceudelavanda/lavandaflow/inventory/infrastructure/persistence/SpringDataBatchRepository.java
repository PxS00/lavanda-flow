package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.application.batch.BatchInventoryRecord;
import com.ceudelavanda.lavandaflow.inventory.application.stock.AvailableStockBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataBatchRepository extends JpaRepository<BatchJpaEntity, UUID> {

    List<BatchJpaEntity> findByInventoryItemId(UUID inventoryItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select batch
        from BatchJpaEntity batch
        where batch.id = :id
        """)
    Optional<BatchJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select batch
        from BatchJpaEntity batch
        where batch.inventoryItemId = :inventoryItemId
        order by batch.id
        """)
    List<BatchJpaEntity> findByInventoryItemIdForUpdate(@Param("inventoryItemId") UUID inventoryItemId);

    List<BatchJpaEntity> findByExpiresAtLessThanEqualAndCurrentQuantityGreaterThan(
        LocalDate expiresAt,
        BigDecimal currentQuantity
    );

    @Query("""
        select new com.ceudelavanda.lavandaflow.inventory.application.stock.AvailableStockBalance(
            batch.inventoryItemId, sum(batch.currentQuantity)
        )
        from BatchJpaEntity batch
        where batch.inventoryItemId in :inventoryItemIds
          and batch.currentQuantity > 0
          and (batch.expiresAt is null or batch.expiresAt > :asOfDate)
        group by batch.inventoryItemId
        """)
    List<AvailableStockBalance> findAvailableStockBalances(
        @Param("inventoryItemIds") Collection<UUID> inventoryItemIds,
        @Param("asOfDate") LocalDate asOfDate
    );

    @Query("""
        select new com.ceudelavanda.lavandaflow.inventory.application.batch.BatchInventoryRecord(
            batch.id,
            batch.inventoryItemId,
            batch.supplierId,
            batch.lotCode,
            batch.initialQuantity,
            batch.currentQuantity,
            batch.receivedAt,
            batch.expiresAt
        )
        from BatchJpaEntity batch
        where batch.inventoryItemId = :inventoryItemId
        order by
            case when batch.expiresAt is null then 1 else 0 end,
            batch.expiresAt asc,
            batch.receivedAt asc,
            batch.id asc
        """)
    List<BatchInventoryRecord> findBatchInventoryByInventoryItemId(
        @Param("inventoryItemId") UUID inventoryItemId
    );
}
