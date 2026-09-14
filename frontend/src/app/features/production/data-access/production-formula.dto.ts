import { InventoryItemUnitOfMeasure } from '../../catalog/data-access/inventory-item.dto';

export type ProductionFormulaKind = 'STANDARD' | 'PACKAGED_FILLING';

/** Transport representation of one persisted production formula ingredient. */
export interface ProductionFormulaIngredientDto {
  readonly inventoryItemId: string;
  readonly quantity: string;
  readonly unitOfMeasure: InventoryItemUnitOfMeasure;
}

/** Transport representation of a current production formula definition. */
export interface ProductionFormulaDto {
  readonly id: string;
  readonly outputInventoryItemId: string;
  readonly outputQuantity: string;
  readonly outputUnitOfMeasure: InventoryItemUnitOfMeasure;
  readonly ingredients: readonly ProductionFormulaIngredientDto[];
  readonly kind?: ProductionFormulaKind;
}

/** Request payload shared by production-formula create and update endpoints. */
export interface UpsertProductionFormulaRequest {
  readonly outputInventoryItemId: string;
  readonly outputQuantity: string;
  readonly ingredients: readonly UpsertProductionFormulaIngredientRequest[];
  readonly kind?: ProductionFormulaKind;
}

export interface UpsertProductionFormulaIngredientRequest {
  readonly inventoryItemId: string;
  readonly quantity: string;
}

export interface ProductionFormulaRequirementsDto {
  readonly formulaId: string;
  readonly outputInventoryItemId: string;
  readonly outputQuantity: string;
  readonly outputUnitOfMeasure: InventoryItemUnitOfMeasure;
  readonly requirements: readonly ProductionFormulaRequirementDto[];
}

export interface ProductionFormulaRequirementDto {
  readonly inventoryItemId: string;
  readonly quantity: string;
  readonly unitOfMeasure: InventoryItemUnitOfMeasure;
}

export function productionFormulaKind(formula: ProductionFormulaDto): ProductionFormulaKind {
  return formula.kind ?? 'STANDARD';
}
