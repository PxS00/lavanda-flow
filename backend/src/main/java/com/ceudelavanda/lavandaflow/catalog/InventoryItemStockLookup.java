package com.ceudelavanda.lavandaflow.catalog;

import java.util.List;
import java.util.UUID;

/** Public catalog boundary for paginated operational stock browsing. */
public interface InventoryItemStockLookup {

    Page findPage(Query query);

    record Query(List<String> categories, int page, int size) {
        public Query {
            categories = List.copyOf(categories);
        }
    }

    record Page(List<Item> content, int page, int size, long totalElements, int totalPages) {
        public Page {
            content = List.copyOf(content);
        }
    }

    record Item(
        UUID id,
        String name,
        String category,
        UnitOfMeasure unitOfMeasure,
        boolean active,
        String essenceReference,
        String productionTypeCode
    ) {
    }
}
