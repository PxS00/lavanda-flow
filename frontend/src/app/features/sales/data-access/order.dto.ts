import { InventoryItemUnitOfMeasure } from '../../catalog/data-access/inventory-item.dto';

export interface OrderLineDto {
  readonly id: string;
  readonly itemId: string;
  readonly itemName: string | null;
  readonly unitOfMeasure: InventoryItemUnitOfMeasure | null;
  readonly quantity: string;
  readonly unitPrice: string;
  readonly amount: string;
  readonly allocations?: readonly {
    readonly batchId: string;
    readonly movementId: string;
    readonly quantity: string;
  }[];
}
export interface OrderDto {
  readonly id: string;
  readonly customerId: string | null;
  readonly customerName: string | null;
  readonly status: 'DRAFT' | 'CONFIRMED' | 'CANCELLED';
  readonly currency: 'BRL';
  readonly lines: readonly OrderLineDto[];
  readonly total: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly confirmedAt?: string | null;
  readonly customerPhone?: string | null;
  readonly customerEmail?: string | null;
}
export interface OrderPageDto {
  readonly content: readonly OrderDto[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}
export interface OrderSearchQuery {
  readonly q?: string;
  readonly customerId?: string;
  readonly from?: string;
  readonly to?: string;
  readonly page: number;
  readonly size: number;
}
export interface SaveDraftRequest {
  readonly customerId: string | null;
  readonly lines: readonly {
    readonly id: string | null;
    readonly itemId: string;
    readonly quantity: string;
    readonly unitPrice: string;
  }[];
}
