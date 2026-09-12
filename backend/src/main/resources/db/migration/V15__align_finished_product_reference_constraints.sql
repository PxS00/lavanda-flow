ALTER TABLE inventory_item
    ADD COLUMN product_gender VARCHAR(3),
    ADD CONSTRAINT inventory_item_gender_valid CHECK (
        product_gender IS NULL OR (
            category IN ('ESSENCE', 'FINISHED_PRODUCT')
            AND product_gender IN ('M', 'F', 'C', 'M/C', 'F/C')
        )
    ),
    DROP CONSTRAINT inventory_item_essence_reference_valid,
    ADD CONSTRAINT inventory_item_essence_reference_valid CHECK (
        essence_reference IS NULL OR (
            category IN ('ESSENCE', 'FINISHED_PRODUCT')
            AND essence_reference ~ '^(00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})$'
        )
    );

DROP INDEX uq_inventory_item_essence_reference;
CREATE UNIQUE INDEX uq_inventory_item_essence_reference
    ON inventory_item (essence_reference)
    WHERE category = 'ESSENCE' AND essence_reference IS NOT NULL;

-- Retain the canonical essence identity so reclassification cannot recycle its reference.
CREATE FUNCTION prevent_canonical_essence_reclassification()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.category = 'ESSENCE' AND OLD.essence_reference IS NOT NULL
        AND NEW.category IS DISTINCT FROM OLD.category THEN
        RAISE EXCEPTION 'canonical essence with an assigned reference cannot change category';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER inventory_item_canonical_essence_category_immutable
    BEFORE UPDATE ON inventory_item
    FOR EACH ROW EXECUTE FUNCTION prevent_canonical_essence_reclassification();
