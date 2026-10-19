import { Routes } from '@angular/router';

export const CUSTOMERS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/customer-list-page/customer-list-page').then((m) => m.CustomerListPage),
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./pages/customer-form-page/customer-form-page').then((m) => m.CustomerFormPage),
  },
  {
    path: ':customerId/edit',
    loadComponent: () =>
      import('./pages/customer-form-page/customer-form-page').then((m) => m.CustomerFormPage),
  },
  {
    path: ':customerId',
    loadComponent: () =>
      import('./pages/customer-detail-page/customer-detail-page').then((m) => m.CustomerDetailPage),
  },
];
