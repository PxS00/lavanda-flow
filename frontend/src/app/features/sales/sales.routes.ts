import { Routes } from '@angular/router';

export const SALES_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./pages/order-list-page').then((m) => m.OrderListPage),
  },
  {
    path: 'new',
    loadComponent: () => import('./pages/order-form-page').then((m) => m.OrderFormPage),
  },
  {
    path: ':orderId/edit',
    loadComponent: () => import('./pages/order-form-page').then((m) => m.OrderFormPage),
  },
  {
    path: ':orderId',
    loadComponent: () => import('./pages/order-detail-page').then((m) => m.OrderDetailPage),
  },
];
