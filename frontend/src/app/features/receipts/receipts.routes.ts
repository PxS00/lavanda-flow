import { Routes } from '@angular/router';

export const RECEIPTS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/stock-receipt-page/stock-receipt-page').then((m) => m.StockReceiptPage),
  },
];
