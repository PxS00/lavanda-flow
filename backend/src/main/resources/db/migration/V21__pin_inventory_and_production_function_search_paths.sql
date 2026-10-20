-- Resolve the four operational advisor notices without replacing trigger bodies.
-- Unqualified signatures intentionally use Flyway's configured application schema.
ALTER FUNCTION prevent_inventory_item_production_reference_mutation() SET search_path = '';
ALTER FUNCTION prevent_inventory_item_essence_reference_deletion() SET search_path = '';
ALTER FUNCTION prevent_production_history_mutation() SET search_path = '';
ALTER FUNCTION prevent_canonical_essence_reclassification() SET search_path = '';
