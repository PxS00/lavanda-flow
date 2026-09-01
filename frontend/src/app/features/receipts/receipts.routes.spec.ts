import { routes } from '../../app.routes';
import { RECEIPTS_ROUTES } from './receipts.routes';

describe('receipt routes', () => {
  it('should lazy load the receipt feature from the application routes', () => {
    const shellRoute = routes.find((route) => route.path === '');
    const receiptRoute = shellRoute?.children?.find((route) => route.path === 'receipts');

    expect(receiptRoute?.loadChildren).toBeTypeOf('function');
  });

  it('should expose the dedicated stock receipt page at the feature root', () => {
    expect(RECEIPTS_ROUTES[0]?.path).toBe('');
    expect(RECEIPTS_ROUTES[0]?.loadComponent).toBeTypeOf('function');
  });
});
