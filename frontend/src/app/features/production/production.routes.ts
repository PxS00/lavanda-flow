import { Routes } from '@angular/router';

export const PRODUCTION_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'formulas',
  },
  {
    path: 'formulas',
    loadComponent: () =>
      import('./pages/production-formula-list-page/production-formula-list-page').then(
        (m) => m.ProductionFormulaListPage,
      ),
  },
  {
    path: 'formulas/new',
    loadComponent: () =>
      import('./pages/production-formula-form-page/production-formula-form-page').then(
        (m) => m.ProductionFormulaFormPage,
      ),
  },
  {
    path: 'formulas/:formulaId',
    loadComponent: () =>
      import('./pages/production-formula-form-page/production-formula-form-page').then(
        (m) => m.ProductionFormulaFormPage,
      ),
  },
];
