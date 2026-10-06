import { OrderDto } from '../data-access/order.dto';
import { InventoryItemDto } from '../../catalog/data-access/inventory-item.dto';

export const product: InventoryItemDto = {
  id: 'item',
  name: 'Perfume',
  category: 'FINISHED_PRODUCT',
  unitOfMeasure: 'UNIT',
  active: true,
  description: null,
  essenceReference: null,
  productionTypeCode: null,
  gender: null,
};
export const order: OrderDto = {
  id: 'order',
  customerId: null,
  customerName: null,
  status: 'DRAFT',
  currency: 'BRL',
  createdAt: '2026-10-06T12:00:00Z',
  updatedAt: '2026-10-06T12:00:00Z',
  total: '1.24',
  lines: [
    {
      id: 'line',
      itemId: product.id,
      itemName: product.name,
      unitOfMeasure: 'UNIT',
      quantity: '1.000000',
      unitPrice: '1.2350',
      amount: '1.24',
    },
  ],
};
