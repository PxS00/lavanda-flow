import {
  INVENTORY_ITEM_CATEGORIES,
  INVENTORY_ITEM_UNITS_OF_MEASURE,
  InventoryItemCategory,
  InventoryItemUnitOfMeasure,
} from './data-access/inventory-item.dto';

export interface InventoryItemDisplayOption<T extends string> {
  readonly value: T;
  readonly label: string;
}

export const INVENTORY_ITEM_CATEGORY_OPTIONS: readonly InventoryItemDisplayOption<InventoryItemCategory>[] =
  INVENTORY_ITEM_CATEGORIES.map((value) => ({ value, label: formatWireValue(value) }));

export const INVENTORY_ITEM_UNIT_OPTIONS: readonly InventoryItemDisplayOption<InventoryItemUnitOfMeasure>[] =
  INVENTORY_ITEM_UNITS_OF_MEASURE.map((value) => ({ value, label: formatWireValue(value) }));

export function inventoryItemCategoryLabel(category: InventoryItemCategory): string {
  return formatWireValue(category);
}

export function inventoryItemUnitLabel(unit: InventoryItemUnitOfMeasure): string {
  return formatWireValue(unit);
}

function formatWireValue(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => `${word.charAt(0).toUpperCase()}${word.slice(1)}`)
    .join(' ');
}
