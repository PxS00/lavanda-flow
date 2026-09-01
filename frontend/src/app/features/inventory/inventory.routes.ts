import { Routes } from '@angular/router';

export const INVENTORY_ROUTES: Routes = [
  {
    path: 'items/:inventoryItemId',
    loadComponent: () =>
      import('./pages/inventory-item-operational-page/inventory-item-operational-page').then(
        (m) => m.InventoryItemOperationalPage,
      ),
  },
];
