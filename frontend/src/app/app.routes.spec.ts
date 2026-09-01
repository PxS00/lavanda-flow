import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';

describe('application routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes)],
    });
  });

  it('should redirect the root route to dashboard', async () => {
    await RouterTestingHarness.create('/');

    const router = TestBed.inject(Router);

    expect(router.url).toBe('/dashboard');
  });

  it('should render the dashboard route', async () => {
    const harness = await RouterTestingHarness.create('/dashboard');

    expect(harness.routeNativeElement?.textContent).toContain('Dashboard');
    expect(harness.routeNativeElement?.textContent).toContain(
      'Operational overview will be available here.',
    );
  });
});
