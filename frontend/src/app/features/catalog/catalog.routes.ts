import { Routes } from '@angular/router';

export const CATALOG_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/inventory-item-list-page/inventory-item-list-page').then(
        (m) => m.InventoryItemListPage,
      ),
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./pages/inventory-item-registration-page/inventory-item-registration-page').then(
        (m) => m.InventoryItemRegistrationPage,
      ),
  },
  {
    path: ':inventoryItemId',
    loadComponent: () =>
      import('./pages/inventory-item-detail-page/inventory-item-detail-page').then(
        (m) => m.InventoryItemDetailPage,
      ),
  },
];
