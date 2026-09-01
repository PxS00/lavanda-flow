import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ApplicationShell } from './application-shell';

describe('ApplicationShell', () => {
  let component: ApplicationShell;
  let fixture: ComponentFixture<ApplicationShell>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ApplicationShell],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ApplicationShell);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should link Inventory navigation to the catalog workspace', () => {
    const links = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];
    const inventoryLink = links.find((link) => link.textContent?.trim() === 'Inventory');

    expect(inventoryLink?.getAttribute('href')).toBe('/catalog');
  });

  it('should link Suppliers navigation to the supplier workspace', () => {
    const links = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];
    const suppliersLink = links.find((link) => link.textContent?.trim() === 'Suppliers');

    expect(suppliersLink?.getAttribute('href')).toBe('/suppliers');
  });
});
