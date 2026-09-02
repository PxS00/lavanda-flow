import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ErrorState } from './error-state';

describe('ErrorState', () => {
  let fixture: ComponentFixture<ErrorState>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorState],
    }).compileComponents();

    fixture = TestBed.createComponent(ErrorState);

    fixture.componentRef.setInput('error', {
      kind: 'validation',
      message: 'Request validation failed.',
      code: 'VALIDATION_ERROR',
      details: {
        name: 'must not be blank',
      },
    });

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the localized error message', () => {
    expect(fixture.nativeElement.textContent).toContain('Revise os dados informados.');
  });

  it('should not render raw backend validation errors', () => {
    expect(fixture.nativeElement.textContent).not.toContain('must not be blank');
  });
});
