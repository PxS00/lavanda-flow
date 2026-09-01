import { Routes } from '@angular/router';

export const SUPPLIERS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/supplier-list-page/supplier-list-page').then((m) => m.SupplierListPage),
  },
];
