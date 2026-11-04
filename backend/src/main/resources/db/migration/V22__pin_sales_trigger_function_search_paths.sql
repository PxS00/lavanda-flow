-- Unqualified signatures intentionally use Flyway's configured application schema.
ALTER FUNCTION prevent_confirmed_sale_mutation() SET search_path = '';
ALTER FUNCTION prevent_sale_line_snapshot_mutation() SET search_path = '';
ALTER FUNCTION prevent_sale_allocation_mutation() SET search_path = '';
ALTER FUNCTION prevent_referenced_movement_mutation() SET search_path = '';
ALTER FUNCTION prevent_sale_stock_correction_mutation() SET search_path = '';
