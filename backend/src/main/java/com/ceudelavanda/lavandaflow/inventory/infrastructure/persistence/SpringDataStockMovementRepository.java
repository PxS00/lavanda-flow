package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.application.history.MovementHistoryEntry;
import com.ceudelavanda.lavandaflow.inventory.domain.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataStockMovementRepository extends JpaRepository<StockMovementJpaEntity, UUID> {

    List<StockMovementJpaEntity> findByBatchIdOrderByOccurredAtAsc(UUID batchId);

    @Query(
        value = """
            select new com.ceudelavanda.lavandaflow.inventory.application.history.MovementHistoryEntry(
                movement.id,
                batch.inventoryItemId,
                movement.batchId,
                batch.lotCode,
                movement.type,
                movement.quantity,
                movement.reason,
                movement.occurredAt
            )
            from StockMovementJpaEntity movement, BatchJpaEntity batch
            where batch.id = movement.batchId
              and (:inventoryItemId is null or batch.inventoryItemId = :inventoryItemId)
              and (:batchId is null or movement.batchId = :batchId)
              and (:type is null or movement.type = :type)
              and (:hasFrom = false or movement.occurredAt >= :fromInstant)
              and (:hasTo = false or movement.occurredAt < :toInstant)
            order by movement.occurredAt desc, movement.id desc
            """,
        countQuery = """
            select count(movement)
            from StockMovementJpaEntity movement, BatchJpaEntity batch
            where batch.id = movement.batchId
              and (:inventoryItemId is null or batch.inventoryItemId = :inventoryItemId)
              and (:batchId is null or movement.batchId = :batchId)
              and (:type is null or movement.type = :type)
              and (:hasFrom = false or movement.occurredAt >= :fromInstant)
              and (:hasTo = false or movement.occurredAt < :toInstant)
            """
    )
    Page<MovementHistoryEntry> findMovementHistory(
        @Param("inventoryItemId") UUID inventoryItemId,
        @Param("batchId") UUID batchId,
        @Param("type") MovementType type,
        @Param("hasFrom") boolean hasFrom,
        @Param("fromInstant") Instant fromInstant,
        @Param("hasTo") boolean hasTo,
        @Param("toInstant") Instant toInstant,
        Pageable pageable
    );
}
