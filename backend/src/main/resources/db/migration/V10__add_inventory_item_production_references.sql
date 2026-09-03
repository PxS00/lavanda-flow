ALTER TABLE inventory_item
    ADD COLUMN essence_reference VARCHAR(3),
    ADD COLUMN production_type_code VARCHAR(3),
    ADD CONSTRAINT inventory_item_essence_reference_valid
        CHECK (
            essence_reference IS NULL
            OR (
                category = 'ESSENCE'
                AND essence_reference ~ '^(00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})$'
            )
        ),
    ADD CONSTRAINT inventory_item_production_type_code_valid
        CHECK (
            production_type_code IS NULL
            OR production_type_code ~ '^[A-Z]{3}$'
        );

CREATE UNIQUE INDEX uq_inventory_item_essence_reference
    ON inventory_item (essence_reference)
    WHERE essence_reference IS NOT NULL;

CREATE FUNCTION prevent_inventory_item_production_reference_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.essence_reference IS NOT NULL
        AND NEW.essence_reference IS DISTINCT FROM OLD.essence_reference THEN
        RAISE EXCEPTION 'assigned essence_reference cannot be changed';
    END IF;

    IF OLD.production_type_code IS NOT NULL
        AND NEW.production_type_code IS DISTINCT FROM OLD.production_type_code THEN
        RAISE EXCEPTION 'assigned production_type_code cannot be changed';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER inventory_item_production_reference_immutable
    BEFORE UPDATE ON inventory_item
    FOR EACH ROW
    EXECUTE FUNCTION prevent_inventory_item_production_reference_mutation();

CREATE FUNCTION prevent_inventory_item_essence_reference_deletion()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.essence_reference IS NOT NULL THEN
        RAISE EXCEPTION 'inventory item with an assigned essence_reference cannot be deleted';
    END IF;

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER inventory_item_assigned_essence_reference_not_deleted
    BEFORE DELETE ON inventory_item
    FOR EACH ROW
    EXECUTE FUNCTION prevent_inventory_item_essence_reference_deletion();
