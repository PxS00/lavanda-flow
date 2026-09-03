CREATE TABLE production_execution (
    id UUID PRIMARY KEY,
    formula_id UUID NOT NULL REFERENCES production_formula(id),
    output_inventory_item_id UUID NOT NULL REFERENCES inventory_item(id),
    output_batch_id UUID NOT NULL UNIQUE REFERENCES inventory_batch(id),
    output_quantity NUMERIC(19, 6) NOT NULL,
    lot_code VARCHAR(255) NOT NULL,
    lot_code_mode VARCHAR(16) NOT NULL,
    production_date DATE NOT NULL,
    output_received_at DATE NOT NULL,
    output_expires_at DATE,
    completed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT production_execution_output_quantity_positive
        CHECK (output_quantity > 0),
    CONSTRAINT production_execution_lot_code_not_blank
        CHECK (btrim(lot_code) <> ''),
    CONSTRAINT production_execution_lot_code_mode_valid
        CHECK (lot_code_mode IN ('GENERATED', 'MANUAL')),
    CONSTRAINT production_execution_expiration_valid
        CHECK (output_expires_at IS NULL OR output_expires_at >= output_received_at)
);

CREATE TABLE production_consumption (
    execution_id UUID NOT NULL REFERENCES production_execution(id),
    position INTEGER NOT NULL,
    source_batch_id UUID NOT NULL REFERENCES inventory_batch(id),
    source_inventory_item_id UUID NOT NULL REFERENCES inventory_item(id),
    movement_id UUID NOT NULL REFERENCES stock_movement(id),
    quantity NUMERIC(19, 6) NOT NULL,
    CONSTRAINT production_consumption_pk
        PRIMARY KEY (execution_id, position),
    CONSTRAINT production_consumption_source_batch_unique
        UNIQUE (execution_id, source_batch_id),
    CONSTRAINT production_consumption_movement_unique
        UNIQUE (movement_id),
    CONSTRAINT production_consumption_quantity_positive
        CHECK (quantity > 0),
    CONSTRAINT production_consumption_position_non_negative
        CHECK (position >= 0)
);

CREATE INDEX production_consumption_source_batch_idx
    ON production_consumption(source_batch_id);

CREATE FUNCTION prevent_production_history_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'completed production history is immutable';
END;
$$;

CREATE TRIGGER production_execution_immutable
BEFORE UPDATE OR DELETE ON production_execution
FOR EACH ROW EXECUTE FUNCTION prevent_production_history_mutation();

CREATE TRIGGER production_consumption_immutable
BEFORE UPDATE OR DELETE ON production_consumption
FOR EACH ROW EXECUTE FUNCTION prevent_production_history_mutation();
