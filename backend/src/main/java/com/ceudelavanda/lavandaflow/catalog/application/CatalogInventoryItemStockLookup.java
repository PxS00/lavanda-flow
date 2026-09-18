package com.ceudelavanda.lavandaflow.catalog.application;

import com.ceudelavanda.lavandaflow.catalog.InventoryItemStockLookup;
import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;

@Service
@RequiredArgsConstructor
class CatalogInventoryItemStockLookup implements InventoryItemStockLookup {

    private final InventoryItemQuery inventoryItemQuery;

    @Override
    @Transactional(readOnly = true)
    public Page findPage(Query query) {
        var categories = query.categories().stream()
            .map(CatalogInventoryItemStockLookup::parseCategory)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var page = inventoryItemQuery.findStockPage(categories, query.page(), query.size());
        return new Page(
            page.content().stream().map(item -> new Item(
                item.id(), item.name(), item.category().name(), item.unitOfMeasure(), item.active(),
                item.essenceReference(), item.productionTypeCode()
            )).toList(),
            page.page(), page.size(), page.totalElements(), page.totalPages()
        );
    }

    private static Category parseCategory(String value) {
        try {
            return Category.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidInventoryItemSearchQueryException("category", "must be a valid category");
        }
    }
}
