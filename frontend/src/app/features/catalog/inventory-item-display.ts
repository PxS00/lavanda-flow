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
  INVENTORY_ITEM_CATEGORIES.map((value) => ({ value, label: inventoryItemCategoryLabel(value) }));

export const INVENTORY_ITEM_UNIT_OPTIONS: readonly InventoryItemDisplayOption<InventoryItemUnitOfMeasure>[] =
  INVENTORY_ITEM_UNITS_OF_MEASURE.map((value) => ({ value, label: inventoryItemUnitLabel(value) }));

export function inventoryItemCategoryLabel(category: InventoryItemCategory): string {
  return {
    ESSENCE: 'Essência', CHEMICAL_INPUT: 'Insumo químico', BASE: 'Base', ALCOHOL: 'Álcool',
    COLORANT: 'Corante', FIXATIVE: 'Fixador', BOTTLE: 'Frasco', VALVE: 'Válvula', CAP: 'Tampa',
    LABEL: 'Rótulo', PACKAGING: 'Embalagem', OTHER: 'Outros',
  }[category];
}

export function inventoryItemUnitLabel(unit: InventoryItemUnitOfMeasure): string {
  return { MILLILITER: 'Mililitro', LITER: 'Litro', GRAM: 'Grama', KILOGRAM: 'Quilograma', UNIT: 'Unidade' }[unit];
}
