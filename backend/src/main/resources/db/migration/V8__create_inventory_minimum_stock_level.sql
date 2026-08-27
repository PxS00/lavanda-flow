CREATE TABLE inventory_minimum_stock_level (
    inventory_item_id UUID PRIMARY KEY
        REFERENCES inventory_item(id)
        ON DELETE RESTRICT,
    minimum_quantity NUMERIC(19, 6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_minimum_stock_level_quantity_positive
        CHECK (minimum_quantity > 0)
);
