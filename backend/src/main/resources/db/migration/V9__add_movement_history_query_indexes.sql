DROP INDEX IF EXISTS idx_stock_movement_batch_occurred_at;

CREATE INDEX idx_stock_movement_batch_occurred_at_id
    ON stock_movement (batch_id, occurred_at DESC, id DESC);

CREATE INDEX idx_inventory_batch_inventory_item_id
    ON inventory_batch (inventory_item_id);

CREATE INDEX idx_stock_movement_occurred_at_id
    ON stock_movement (occurred_at DESC, id DESC);
