import { routes } from '../../app.routes';
import { authGuard } from '../../core/auth/auth.guard';
import { SALES_ROUTES } from './sales.routes';
import { OrderListPage } from './pages/order-list-page';
import { OrderFormPage } from './pages/order-form-page';
import { OrderDetailPage } from './pages/order-detail-page';

describe('authenticated sales routes', () => {
  it('lazy loads list, new, edit and detail under both session guards', async () => {
    const shell = routes.find((route) => route.path === '');
    expect(shell?.canActivate).toContain(authGuard);
    expect(shell?.canActivateChild).toContain(authGuard);
    const sales = shell?.children?.find((route) => route.path === 'sales');
    expect(await (sales?.loadChildren as () => Promise<unknown>)()).toBe(SALES_ROUTES);
    const components = [OrderListPage, OrderFormPage, OrderFormPage, OrderDetailPage];
    expect(SALES_ROUTES.map((route) => route.path)).toEqual([
      '',
      'new',
      ':orderId/edit',
      ':orderId',
    ]);
    for (let i = 0; i < components.length; i++) {
      expect(await (SALES_ROUTES[i].loadComponent as () => Promise<unknown>)()).toBe(components[i]);
    }
  });
});
