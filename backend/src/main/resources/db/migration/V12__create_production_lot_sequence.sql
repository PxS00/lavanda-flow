CREATE TABLE production_lot_sequence (
    production_type_code VARCHAR(3) NOT NULL,
    essence_reference VARCHAR(3) NOT NULL,
    production_year INTEGER NOT NULL,
    production_month SMALLINT NOT NULL,
    last_sequence SMALLINT NOT NULL,
    CONSTRAINT production_lot_sequence_pk
        PRIMARY KEY (production_type_code, essence_reference, production_year, production_month),
    CONSTRAINT production_lot_sequence_production_type_code_valid
        CHECK (production_type_code ~ '^[A-Z]{3}$'),
    CONSTRAINT production_lot_sequence_essence_reference_valid
        CHECK (essence_reference ~ '^[0-9]{3}$'),
    CONSTRAINT production_lot_sequence_month_valid
        CHECK (production_month BETWEEN 1 AND 12),
    CONSTRAINT production_lot_sequence_last_sequence_valid
        CHECK (last_sequence BETWEEN 1 AND 999)
);
