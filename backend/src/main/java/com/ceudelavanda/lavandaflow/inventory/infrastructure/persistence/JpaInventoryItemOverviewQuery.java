package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.application.overview.InventoryItemOverviewMetrics;
import com.ceudelavanda.lavandaflow.inventory.application.overview.InventoryItemOverviewQuery;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** PostgreSQL projection for one inventory item's operational overview metrics. */
@Repository
@RequiredArgsConstructor
class JpaInventoryItemOverviewQuery implements InventoryItemOverviewQuery {

    private static final String BATCH_METRICS_SQL = """
        SELECT
            batch.inventory_item_id,
            COALESCE(SUM(batch.current_quantity), CAST(0 AS NUMERIC(19, 6))),
            COALESCE(SUM(
                CASE
                    WHEN batch.current_quantity > 0
                         AND (batch.expires_at IS NULL OR batch.expires_at > :asOfDate)
                    THEN batch.current_quantity
                    ELSE CAST(0 AS NUMERIC(19, 6))
                END
            ), CAST(0 AS NUMERIC(19, 6))),
            COUNT(*) FILTER (WHERE batch.current_quantity > 0),
            MIN(batch.expires_at) FILTER (
                WHERE batch.current_quantity > 0
                  AND batch.expires_at > :asOfDate
            ),
            COUNT(*) FILTER (
                WHERE batch.current_quantity > 0
                  AND batch.expires_at IS NOT NULL
                  AND batch.expires_at <= :asOfDate
            ),
            COUNT(*) FILTER (
                WHERE batch.current_quantity > 0
                  AND batch.expires_at > :asOfDate
                  AND batch.expires_at <= :expirationCutoff
            )
        FROM inventory_batch batch
        WHERE batch.inventory_item_id IN (:inventoryItemIds)
        GROUP BY batch.inventory_item_id
        """;

    private static final String MINIMUM_SQL = """
        SELECT minimum.inventory_item_id, minimum.minimum_quantity
        FROM inventory_minimum_stock_level minimum
        WHERE minimum.inventory_item_id IN (:inventoryItemIds)
        """;

    private final EntityManager entityManager;

    @Override
    public InventoryItemOverviewMetrics findMetrics(
        UUID inventoryItemId,
        LocalDate asOfDate,
        LocalDate expirationCutoff
    ) {
        return findMetrics(Set.of(inventoryItemId), asOfDate, expirationCutoff).get(inventoryItemId);
    }

    @Override
    public Map<UUID, InventoryItemOverviewMetrics> findMetrics(
        Set<UUID> inventoryItemIds,
        LocalDate asOfDate,
        LocalDate expirationCutoff
    ) {
        if (inventoryItemIds.isEmpty()) {
            return Map.of();
        }

        var minimums = new HashMap<UUID, BigDecimal>();
        entityManager.createNativeQuery(MINIMUM_SQL)
            .setParameter("inventoryItemIds", inventoryItemIds)
            .getResultList()
            .forEach(value -> {
                var row = (Object[]) value;
                minimums.put((UUID) row[0], (BigDecimal) row[1]);
            });

        var result = new HashMap<UUID, InventoryItemOverviewMetrics>();
        inventoryItemIds.forEach(id -> result.put(id, InventoryItemOverviewMetrics.zero(minimums.get(id))));
        entityManager.createNativeQuery(BATCH_METRICS_SQL)
            .setParameter("inventoryItemIds", inventoryItemIds)
            .setParameter("asOfDate", asOfDate)
            .setParameter("expirationCutoff", expirationCutoff)
            .getResultList()
            .forEach(value -> {
                var row = (Object[]) value;
                var id = (UUID) row[0];
                result.put(id, new InventoryItemOverviewMetrics(
                    (BigDecimal) row[1],
                    (BigDecimal) row[2],
                    minimums.get(id),
                    ((Number) row[3]).longValue(),
                    toLocalDate(row[4]),
                    ((Number) row[5]).longValue(),
                    ((Number) row[6]).longValue()
                ));
            });
        return Map.copyOf(result);
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        throw new IllegalStateException("Unsupported database date value: " + value.getClass().getName());
    }
}
