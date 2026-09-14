export const INVENTORY_ITEM_CATEGORIES = [
  'ESSENCE',
  'FINISHED_PRODUCT',
  'CHEMICAL_INPUT',
  'BASE',
  'ALCOHOL',
  'COLORANT',
  'FIXATIVE',
  'BOTTLE',
  'VALVE',
  'CAP',
  'LABEL',
  'PACKAGING',
  'OTHER',
] as const;

export type InventoryItemCategory = (typeof INVENTORY_ITEM_CATEGORIES)[number];

export const INVENTORY_ITEM_UNITS_OF_MEASURE = [
  'MILLILITER',
  'LITER',
  'GRAM',
  'KILOGRAM',
  'UNIT',
] as const;

export type InventoryItemUnitOfMeasure = (typeof INVENTORY_ITEM_UNITS_OF_MEASURE)[number];

export const PRODUCT_GENDERS = ['M', 'F', 'C', 'M/C', 'F/C'] as const;
export type ProductGender = (typeof PRODUCT_GENDERS)[number];

/** Transport representation of an inventory item returned by the catalog API. */
export interface InventoryItemDto {
  readonly id: string;
  readonly name: string;
  readonly description: string | null;
  readonly category: InventoryItemCategory;
  readonly unitOfMeasure: InventoryItemUnitOfMeasure;
  readonly active: boolean;
  readonly essenceReference: string | null;
  readonly productionTypeCode: string | null;
  readonly gender: ProductGender | null;
}

/** Transport representation of one paginated catalog API response. */
export interface InventoryItemPageDto {
  readonly content: readonly InventoryItemDto[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

/** Query parameters accepted by the inventory-item search endpoint. */
export interface InventoryItemSearchQuery {
  readonly name?: string;
  readonly category?: InventoryItemCategory;
  readonly active?: boolean;
  readonly page: number;
  readonly size: number;
}

/** Request payload accepted by the inventory-item registration endpoint. */
export interface RegisterInventoryItemRequest {
  readonly name: string;
  readonly description: string | null;
  readonly category: InventoryItemCategory;
  readonly unitOfMeasure: InventoryItemUnitOfMeasure;
  readonly essenceReference: string | null;
  readonly productionTypeCode: string | null;
  readonly gender: ProductGender | null;
}
