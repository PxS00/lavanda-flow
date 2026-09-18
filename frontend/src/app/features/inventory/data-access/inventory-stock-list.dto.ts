import { InventoryItemCategory } from '../../catalog/data-access/inventory-item.dto';
import { InventoryUnitOfMeasure } from './inventory-operations.dto';

export interface InventoryStockListEntryDto {
  readonly inventoryItemId: string;
  readonly name: string;
  readonly category: InventoryItemCategory;
  readonly unitOfMeasure: InventoryUnitOfMeasure;
  readonly active: boolean;
  readonly essenceReference: string | null;
  readonly productionTypeCode: string | null;
  readonly totalCurrentQuantity: string;
  readonly availableQuantity: string;
  readonly minimumQuantity: string | null;
  readonly lowStock: boolean;
  readonly outOfStock: boolean;
  readonly nonZeroBatchCount: number;
  readonly nearestExpiration: string | null;
}

export interface InventoryStockListPageDto {
  readonly content: readonly InventoryStockListEntryDto[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly asOfDate: string;
  readonly expirationWindowDays: number;
}

export interface InventoryStockListQuery {
  readonly categories: readonly InventoryItemCategory[];
  readonly page: number;
  readonly size: number;
}
