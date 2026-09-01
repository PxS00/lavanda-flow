import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EmptyState } from './empty-state';

describe('EmptyState', () => {
  let fixture: ComponentFixture<EmptyState>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptyState],
    }).compileComponents();

    fixture = TestBed.createComponent(EmptyState);

    fixture.componentRef.setInput('title', 'No items found');
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the required title', () => {
    expect(fixture.nativeElement.textContent).toContain('No items found');
  });

  it('should render the optional message when provided', () => {
    fixture.componentRef.setInput('message', 'Create an item to get started.');

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Create an item to get started.');
  });
});
