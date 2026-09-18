import { Routes } from '@angular/router';

export const SUPPLIERS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/supplier-list-page/supplier-list-page').then((m) => m.SupplierListPage),
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./pages/supplier-registration-page/supplier-registration-page').then(
        (m) => m.SupplierRegistrationPage,
      ),
  },
  {
    path: ':supplierId/edit',
    loadComponent: () =>
      import('./pages/supplier-edit-page/supplier-edit-page').then((m) => m.SupplierEditPage),
  },
  {
    path: ':supplierId',
    loadComponent: () =>
      import('./pages/supplier-detail-page/supplier-detail-page').then((m) => m.SupplierDetailPage),
  },
];
