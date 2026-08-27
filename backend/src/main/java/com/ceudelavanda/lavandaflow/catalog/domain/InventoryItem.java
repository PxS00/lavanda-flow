package com.ceudelavanda.lavandaflow.catalog.domain;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;

import lombok.Getter;
import java.util.UUID;

/**
 * Represents a material controlled by the inventory catalog.
 *
 * <p>An inventory item defines identity and classification only. Stock
 * quantities, batches, expiration dates and movements belong to the
 * inventory module.</p>
 */
@Getter
public class InventoryItem {

    private final UUID id;
    private String name;
    private String description;
    private Category category;
    private UnitOfMeasure unitOfMeasure;
    private boolean active;

    public InventoryItem(
        UUID id,
        String name,
        String description,
        Category category,
        UnitOfMeasure unitOfMeasure,
        boolean active
    ) {
        this.id = requireNonNull(id, "id");
        this.name = requireName(name);
        this.description = normalizeDescription(description);
        this.category = requireNonNull(category, "category");
        this.unitOfMeasure = requireNonNull(unitOfMeasure, "unitOfMeasure");
        this.active = active;
    }

    public static InventoryItem create(
        String name,
        String description,
        Category category,
        UnitOfMeasure unitOfMeasure
    ) {
        return new InventoryItem(
            UUID.randomUUID(),
            name,
            description,
            category,
            unitOfMeasure,
            true
        );
    }

    public void rename(String name) {
        this.name = requireName(name);
    }

    public void changeDescription(String description) {
        this.description = normalizeDescription(description);
    }

    public void changeCategory(Category category) {
        this.category = requireNonNull(category, "category");
    }

    public void changeUnitOfMeasure(UnitOfMeasure unitOfMeasure) {
        this.unitOfMeasure = requireNonNull(unitOfMeasure, "unitOfMeasure");
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }

        return name.trim();
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }

        var normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }

        return value;
    }
}
