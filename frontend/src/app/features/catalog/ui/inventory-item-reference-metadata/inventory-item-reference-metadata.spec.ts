import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InventoryItemReferenceMetadata } from './inventory-item-reference-metadata';

describe('InventoryItemReferenceMetadata', () => {
  let fixture: ComponentFixture<InventoryItemReferenceMetadata>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InventoryItemReferenceMetadata],
    }).compileComponents();
    fixture = TestBed.createComponent(InventoryItemReferenceMetadata);
  });

  it('should render both backend values unchanged', () => {
    fixture.componentRef.setInput('essenceReference', '027');
    fixture.componentRef.setInput('productionTypeCode', 'BHC');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ref. essência:');
    expect(fixture.nativeElement.textContent).toContain('Cód. produção:');
    expect(codeValues()).toEqual(['027', 'BHC']);
  });

  it('should render only the essence reference when assigned', () => {
    fixture.componentRef.setInput('essenceReference', '027');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ref. essência:');
    expect(codeValues()).toEqual(['027']);
    expect(fixture.nativeElement.textContent).not.toContain('Cód. produção');
  });

  it('should render only the production type code when assigned', () => {
    fixture.componentRef.setInput('productionTypeCode', 'BHC');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Ref. essência');
    expect(fixture.nativeElement.textContent).toContain('Cód. produção:');
    expect(codeValues()).toEqual(['BHC']);
  });

  it('should render no compact metadata or placeholder when both values are null', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent.trim()).toBe('');
    expect(fixture.nativeElement.querySelector('.reference-metadata')).toBeNull();
  });

  function codeValues(): string[] {
    return Array.from(
      fixture.nativeElement.querySelectorAll('code') as NodeListOf<HTMLElement>,
    ).map((element) => element.textContent ?? '');
  }
});
