-- Additive sales-owned draft storage. References cross module boundaries as UUIDs, never JPA relations.
CREATE TABLE sales_order (
    id UUID PRIMARY KEY,
    customer_id UUID REFERENCES customer(id),
    status VARCHAR(9) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    total NUMERIC(19,2) NOT NULL CHECK (total >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE sales_order_line (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES sales_order(id),
    item_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    quantity NUMERIC(19,6) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    amount NUMERIC(19,2) NOT NULL CHECK (amount >= 0 AND amount = round(quantity * unit_price, 2)),
    CONSTRAINT uq_sales_order_line_item UNIQUE (order_id, item_id)
);
-- Supports draft date-ordered pages and customer-filtered draft pages; UUID breaks timestamp ties.
CREATE INDEX idx_sales_order_status_created_id ON sales_order (status, created_at DESC, id);
CREATE INDEX idx_sales_order_customer_status_created_id ON sales_order (customer_id, status, created_at DESC, id);
