CREATE TABLE production_formula (
    id UUID PRIMARY KEY,
    output_inventory_item_id UUID NOT NULL REFERENCES inventory_item(id),
    output_quantity NUMERIC(19, 6) NOT NULL,
    output_unit_of_measure VARCHAR(32) NOT NULL,
    CONSTRAINT production_formula_output_quantity_positive
        CHECK (output_quantity > 0)
);

CREATE TABLE production_formula_ingredient (
    formula_id UUID NOT NULL REFERENCES production_formula(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    inventory_item_id UUID NOT NULL REFERENCES inventory_item(id),
    quantity NUMERIC(19, 6) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    CONSTRAINT production_formula_ingredient_pk
        PRIMARY KEY (formula_id, position),
    CONSTRAINT production_formula_ingredient_item_unique
        UNIQUE (formula_id, inventory_item_id),
    CONSTRAINT production_formula_ingredient_quantity_positive
        CHECK (quantity > 0)
);
