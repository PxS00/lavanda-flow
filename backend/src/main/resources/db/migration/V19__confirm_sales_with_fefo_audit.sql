-- Additive expansion; existing movements and genealogy are never rewritten.
ALTER TABLE stock_movement ADD COLUMN reference_type VARCHAR(32), ADD COLUMN reference_id UUID, ADD COLUMN reference_line_id UUID,
    ADD CONSTRAINT ck_stock_movement_reference CHECK (
        (reference_type IS NULL AND reference_id IS NULL AND reference_line_id IS NULL) OR
        (reference_type IS NOT NULL AND length(trim(reference_type)) > 0 AND reference_id IS NOT NULL AND reference_line_id IS NOT NULL));
CREATE UNIQUE INDEX uq_stock_movement_source_batch ON stock_movement (reference_type, reference_id, reference_line_id, batch_id) WHERE reference_type IS NOT NULL;
ALTER TABLE sales_order ADD COLUMN customer_name VARCHAR(160), ADD COLUMN customer_phone VARCHAR(16), ADD COLUMN customer_email VARCHAR(254), ADD COLUMN confirmed_at TIMESTAMPTZ;
ALTER TABLE sales_order_line ADD COLUMN item_name VARCHAR(255), ADD COLUMN unit_of_measure VARCHAR(50),
    ADD CONSTRAINT ck_sale_line_snapshot CHECK ((item_name IS NULL AND unit_of_measure IS NULL) OR (item_name IS NOT NULL AND unit_of_measure IS NOT NULL AND unit_of_measure IN ('UNIT', 'MILLILITER')));
CREATE TABLE sale_allocation (
    id UUID PRIMARY KEY,
    line_id UUID NOT NULL REFERENCES sales_order_line(id),
    batch_id UUID NOT NULL REFERENCES inventory_batch(id),
    movement_id UUID NOT NULL REFERENCES stock_movement(id),
    quantity NUMERIC(19,6) NOT NULL CHECK (quantity > 0),
    position INTEGER NOT NULL CHECK (position >= 0),
    CONSTRAINT uq_sale_allocation_line_batch UNIQUE (line_id, batch_id),
    CONSTRAINT uq_sale_allocation_movement UNIQUE (movement_id)
);

-- Follow production's database defense for immutable completed history.
CREATE FUNCTION prevent_confirmed_sale_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'CONFIRMED' THEN
        RAISE EXCEPTION 'confirmed sale history is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER confirmed_sale_immutable BEFORE UPDATE OR DELETE ON sales_order
    FOR EACH ROW EXECUTE FUNCTION prevent_confirmed_sale_mutation();

CREATE FUNCTION prevent_sale_line_snapshot_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.item_name IS NOT NULL THEN
        RAISE EXCEPTION 'confirmed sale line history is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER sale_line_snapshot_immutable BEFORE UPDATE OR DELETE ON sales_order_line
    FOR EACH ROW EXECUTE FUNCTION prevent_sale_line_snapshot_mutation();

CREATE FUNCTION prevent_sale_allocation_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'sale allocation history is immutable';
END;
$$;
CREATE TRIGGER sale_allocation_immutable BEFORE UPDATE OR DELETE ON sale_allocation
    FOR EACH ROW EXECUTE FUNCTION prevent_sale_allocation_mutation();

-- Nullable legacy references remain compatible with old inventory writers.
CREATE FUNCTION prevent_referenced_movement_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.reference_type IS NOT NULL THEN
        RAISE EXCEPTION 'referenced stock movement history is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER referenced_stock_movement_immutable BEFORE UPDATE OR DELETE ON stock_movement
    FOR EACH ROW EXECUTE FUNCTION prevent_referenced_movement_mutation();
