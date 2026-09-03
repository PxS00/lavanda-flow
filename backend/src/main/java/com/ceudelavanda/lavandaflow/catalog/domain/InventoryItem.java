package com.ceudelavanda.lavandaflow.catalog.domain;

import com.ceudelavanda.lavandaflow.catalog.UnitOfMeasure;

import lombok.Getter;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Represents a material controlled by the inventory catalog.
 *
 * <p>An inventory item defines identity and classification only. Stock
 * quantities, batches, expiration dates and movements belong to the
 * inventory module.</p>
 */
@Getter
public class InventoryItem {

    private static final Pattern ESSENCE_REFERENCE_PATTERN = Pattern.compile(
        "(?:00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})"
    );
    private static final Pattern PRODUCTION_TYPE_CODE_PATTERN = Pattern.compile("[A-Z]{3}");

    private final UUID id;
    private String name;
    private String description;
    private Category category;
    private UnitOfMeasure unitOfMeasure;
    private boolean active;
    private String essenceReference;
    private String productionTypeCode;

    public InventoryItem(
        UUID id,
        String name,
        String description,
        Category category,
        UnitOfMeasure unitOfMeasure,
        boolean active
    ) {
        this(id, name, description, category, unitOfMeasure, active, null, null);
    }

    public InventoryItem(
        UUID id,
        String name,
        String description,
        Category category,
        UnitOfMeasure unitOfMeasure,
        boolean active,
        String essenceReference,
        String productionTypeCode
    ) {
        this.id = requireNonNull(id, "id");
        this.name = requireName(name);
        this.description = normalizeDescription(description);
        this.category = requireNonNull(category, "category");
        this.unitOfMeasure = requireNonNull(unitOfMeasure, "unitOfMeasure");
        this.active = active;
        this.essenceReference = requireEssenceReference(essenceReference, category);
        this.productionTypeCode = requireProductionTypeCode(productionTypeCode);
    }

    public static InventoryItem create(
        String name,
        String description,
        Category category,
        UnitOfMeasure unitOfMeasure
    ) {
        return create(name, description, category, unitOfMeasure, null, null);
    }

    public static InventoryItem create(
        String name,
        String description,
        Category category,
        UnitOfMeasure unitOfMeasure,
        String essenceReference,
        String productionTypeCode
    ) {
        return new InventoryItem(
            UUID.randomUUID(),
            name,
            description,
            category,
            unitOfMeasure,
            true,
            essenceReference,
            productionTypeCode
        );
    }

    public void rename(String name) {
        this.name = requireName(name);
    }

    public void changeDescription(String description) {
        this.description = normalizeDescription(description);
    }

    public void changeCategory(Category category) {
        var newCategory = requireNonNull(category, "category");
        if (essenceReference != null && newCategory != Category.ESSENCE) {
            throw new IllegalArgumentException("essenceReference is only valid for ESSENCE items");
        }
        this.category = newCategory;
    }

    public void changeUnitOfMeasure(UnitOfMeasure unitOfMeasure) {
        this.unitOfMeasure = requireNonNull(unitOfMeasure, "unitOfMeasure");
    }

    /**
     * Assigns the stable reference for an essence that does not already have one.
     * An assigned reference can only be submitted again unchanged.
     */
    public void assignEssenceReference(String essenceReference) {
        if (this.essenceReference != null && !this.essenceReference.equals(essenceReference)) {
            throw new IllegalStateException("assigned essenceReference cannot be changed");
        }
        this.essenceReference = requireEssenceReference(essenceReference, category);
    }

    /**
     * Assigns the stable production type code when it has not yet been assigned.
     * An assigned code can only be submitted again unchanged.
     */
    public void assignProductionTypeCode(String productionTypeCode) {
        if (this.productionTypeCode != null && !this.productionTypeCode.equals(productionTypeCode)) {
            throw new IllegalStateException("assigned productionTypeCode cannot be changed");
        }
        this.productionTypeCode = requireProductionTypeCode(productionTypeCode);
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

    private static String requireEssenceReference(String essenceReference, Category category) {
        if (essenceReference == null) {
            return null;
        }
        if (category != Category.ESSENCE) {
            throw new IllegalArgumentException("essenceReference is only valid for ESSENCE items");
        }
        if (!ESSENCE_REFERENCE_PATTERN.matcher(essenceReference).matches()) {
            throw new IllegalArgumentException("essenceReference must be a three-digit value from 001 through 999");
        }
        return essenceReference;
    }

    private static String requireProductionTypeCode(String productionTypeCode) {
        if (productionTypeCode == null) {
            return null;
        }
        if (!PRODUCTION_TYPE_CODE_PATTERN.matcher(productionTypeCode).matches()) {
            throw new IllegalArgumentException("productionTypeCode must be exactly three uppercase letters");
        }
        return productionTypeCode;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }

        return value;
    }
}
