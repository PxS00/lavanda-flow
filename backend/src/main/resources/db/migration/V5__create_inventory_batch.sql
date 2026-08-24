CREATE TABLE inventory_batch (
    id UUID PRIMARY KEY,
    inventory_item_id UUID NOT NULL REFERENCES inventory_item(id),
    supplier_id UUID REFERENCES supplier(id),
    lot_code VARCHAR(255),
    initial_quantity NUMERIC(19, 6) NOT NULL,
    current_quantity NUMERIC(19, 6) NOT NULL,
    received_at DATE NOT NULL,
    expires_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_batch_initial_quantity_positive
        CHECK (initial_quantity > 0),
    CONSTRAINT inventory_batch_current_quantity_non_negative
        CHECK (current_quantity >= 0)
);
