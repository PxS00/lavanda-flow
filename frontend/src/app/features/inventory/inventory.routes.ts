import { Routes } from '@angular/router';

export const INVENTORY_ROUTES: Routes = [
  { path: '', redirectTo: 'all', pathMatch: 'full' },
  ...[
    { path: 'all', title: 'Estoque', helperText: 'Consulte todos os itens e seus saldos operacionais.' },
    { path: 'finished-products', title: 'Produtos finalizados', helperText: 'Consulte produtos a granel e apresentações acabadas.', categories: ['FINISHED_PRODUCT'] },
    { path: 'essences', title: 'Essências', helperText: 'Consulte o estoque operacional de essências.', categories: ['ESSENCE'] },
    { path: 'inputs', title: 'Insumos', helperText: 'Consulte bases, álcool e demais insumos de produção.', categories: ['BASE', 'ALCOHOL', 'CHEMICAL_INPUT', 'COLORANT', 'FIXATIVE'] },
    { path: 'packaging', title: 'Embalagens e componentes', helperText: 'Consulte frascos, válvulas, tampas, rótulos e embalagens.', categories: ['BOTTLE', 'VALVE', 'CAP', 'LABEL', 'PACKAGING'] },
  ].map(({ path, ...data }) => ({
    path,
    data,
    loadComponent: () =>
      import('./pages/inventory-stock-list-page/inventory-stock-list-page').then(
        (m) => m.InventoryStockListPage,
      ),
  })),
  {
    path: 'alerts',
    loadComponent: () =>
      import('./pages/inventory-alerts-page/inventory-alerts-page').then(
        (m) => m.InventoryAlertsPage,
      ),
  },
  {
    path: 'items/:inventoryItemId',
    loadComponent: () =>
      import('./pages/inventory-item-operational-page/inventory-item-operational-page').then(
        (m) => m.InventoryItemOperationalPage,
      ),
  },
];
