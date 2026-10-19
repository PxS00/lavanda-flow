CREATE TABLE customer (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    phone VARCHAR(16),
    email VARCHAR(254),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_customer_name CHECK (name = BTRIM(name) AND name !~ '^[[:space:]]*$'),
    CONSTRAINT ck_customer_phone CHECK (phone IS NULL OR phone ~ '^\+?[0-9]{7,15}$'),
    CONSTRAINT ck_customer_email CHECK (email IS NULL OR (email = BTRIM(email) AND email !~ '^[[:space:]]*$'))
);

CREATE INDEX idx_customer_active_name_id ON customer (active, name, id);
