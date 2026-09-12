ALTER TABLE production_formula
    ADD COLUMN formula_kind VARCHAR(32);

UPDATE production_formula
SET formula_kind = 'STANDARD'
WHERE formula_kind IS NULL;

ALTER TABLE production_formula
    ALTER COLUMN formula_kind SET NOT NULL;

ALTER TABLE production_formula
    ADD CONSTRAINT production_formula_kind_valid
        CHECK (formula_kind IN ('STANDARD', 'PACKAGED_FILLING'));

CREATE TABLE packaged_production_lot_sequence (
    production_year INTEGER NOT NULL,
    production_month INTEGER NOT NULL,
    last_sequence INTEGER NOT NULL,
    CONSTRAINT packaged_production_lot_sequence_pk
        PRIMARY KEY (production_year, production_month),
    CONSTRAINT packaged_production_lot_sequence_month_valid
        CHECK (production_month BETWEEN 1 AND 12),
    CONSTRAINT packaged_production_lot_sequence_value_valid
        CHECK (last_sequence BETWEEN 1 AND 999)
);
