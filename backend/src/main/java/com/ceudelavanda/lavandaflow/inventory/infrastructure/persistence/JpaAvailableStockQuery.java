package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.application.AvailableStockBalance;
import com.ceudelavanda.lavandaflow.inventory.application.AvailableStockQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
class JpaAvailableStockQuery implements AvailableStockQuery {

    private final SpringDataBatchRepository repository;

    JpaAvailableStockQuery(SpringDataBatchRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AvailableStockBalance> findAvailableStockByInventoryItemIds(
        Collection<UUID> inventoryItemIds,
        LocalDate asOfDate
    ) {
        if (inventoryItemIds.isEmpty()) {
            return List.of();
        }

        return repository.findAvailableStockBalances(inventoryItemIds, asOfDate);
    }
}
