CREATE TABLE inventory_item (
                              id UUID PRIMARY KEY,
                              name VARCHAR(255) NOT NULL,
                              description TEXT,
                              category VARCHAR(50) NOT NULL,
                              default_unit VARCHAR(50) NOT NULL,
                              active BOOLEAN NOT NULL DEFAULT TRUE,
                              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
