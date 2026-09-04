import { InventoryItemUnitOfMeasure } from '../../catalog/data-access/inventory-item.dto';

/** Transport representation of one persisted production formula ingredient. */
export interface ProductionFormulaIngredientDto {
  readonly inventoryItemId: string;
  readonly quantity: number;
  readonly unitOfMeasure: InventoryItemUnitOfMeasure;
}

/** Transport representation of a current production formula definition. */
export interface ProductionFormulaDto {
  readonly id: string;
  readonly outputInventoryItemId: string;
  readonly outputQuantity: number;
  readonly outputUnitOfMeasure: InventoryItemUnitOfMeasure;
  readonly ingredients: readonly ProductionFormulaIngredientDto[];
}

/** Request payload shared by production-formula create and update endpoints. */
export interface UpsertProductionFormulaRequest {
  readonly outputInventoryItemId: string;
  readonly outputQuantity: number;
  readonly ingredients: readonly UpsertProductionFormulaIngredientRequest[];
}

export interface UpsertProductionFormulaIngredientRequest {
  readonly inventoryItemId: string;
  readonly quantity: number;
}
