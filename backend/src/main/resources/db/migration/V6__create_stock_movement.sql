CREATE TABLE stock_movement (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES inventory_batch(id),
    movement_type VARCHAR(50) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stock_movement_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_stock_movement_batch_occurred_at
    ON stock_movement (batch_id, occurred_at);
