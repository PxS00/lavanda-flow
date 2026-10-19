-- Build the replacement before removing V19's broader index. CONSUMPTION keeps its original defense.
CREATE UNIQUE INDEX uq_stock_movement_source_batch_non_return
    ON stock_movement (reference_type, reference_id, reference_line_id, batch_id)
    WHERE reference_type IS NOT NULL AND movement_type <> 'ADJUSTMENT_IN';
DROP INDEX uq_stock_movement_source_batch;

ALTER TABLE sales_order_line ADD CONSTRAINT uq_sale_line_order_identity UNIQUE (order_id, id);
CREATE TABLE sale_stock_correction (
    sale_id UUID NOT NULL REFERENCES sales_order(id),
    id UUID NOT NULL,
    line_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    quantity NUMERIC(19,6) NOT NULL CHECK (quantity > 0),
    reason VARCHAR(255) NOT NULL CHECK (length(trim(reason)) > 0),
    inspected BOOLEAN NOT NULL CHECK (inspected),
    accepted BOOLEAN NOT NULL CHECK (accepted),
    actor VARCHAR(100) NOT NULL CHECK (length(trim(actor)) > 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    movement_id UUID NOT NULL UNIQUE REFERENCES stock_movement(id),
    PRIMARY KEY (sale_id, id),
    FOREIGN KEY (sale_id, line_id) REFERENCES sales_order_line(order_id, id),
    FOREIGN KEY (line_id, batch_id) REFERENCES sale_allocation(line_id, batch_id)
);

CREATE FUNCTION prevent_sale_stock_correction_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'sale stock correction history is immutable';
END;
$$;
CREATE TRIGGER sale_stock_correction_immutable BEFORE UPDATE OR DELETE ON sale_stock_correction
    FOR EACH ROW EXECUTE FUNCTION prevent_sale_stock_correction_mutation();
