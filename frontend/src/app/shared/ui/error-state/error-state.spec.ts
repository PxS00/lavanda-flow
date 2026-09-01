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
      fieldErrors: {
        name: 'must not be blank',
      },
    });

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the error message', () => {
    expect(fixture.nativeElement.textContent).toContain('Request validation failed.');
  });

  it('should render field validation errors', () => {
    expect(fixture.nativeElement.textContent).toContain('name:');

    expect(fixture.nativeElement.textContent).toContain('must not be blank');
  });
});
