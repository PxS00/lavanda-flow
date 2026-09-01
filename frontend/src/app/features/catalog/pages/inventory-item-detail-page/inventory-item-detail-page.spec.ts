import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { InventoryItemDto } from '../../data-access/inventory-item.dto';
import { InventoryItemApiService } from '../../data-access/inventory-item-api.service';
import { InventoryItemDetailPage } from './inventory-item-detail-page';

describe('InventoryItemDetailPage', () => {
  const inventoryItemId = 'bd194732-51cf-4f73-bc5d-3a9f9337adcc';
  const item: InventoryItemDto = {
    id: inventoryItemId,
    name: 'Lavender Essence',
    description: 'Floral raw material',
    category: 'ESSENCE',
    unitOfMeasure: 'MILLILITER',
    active: true,
  };

  let fixture: ComponentFixture<InventoryItemDetailPage>;
  let response: Subject<InventoryItemDto>;
  let routeParams: Subject<ParamMap>;
  let getById: ReturnType<typeof vi.fn<(id: string) => Observable<InventoryItemDto>>>;

  beforeEach(async () => {
    response = new Subject<InventoryItemDto>();
    routeParams = new Subject<ParamMap>();
    getById = vi.fn(() => response);

    await TestBed.configureTestingModule({
      imports: [InventoryItemDetailPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: routeParams },
        },
        { provide: InventoryItemApiService, useValue: { getById } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InventoryItemDetailPage);
    fixture.detectChanges();
    routeParams.next(convertToParamMap({ inventoryItemId }));
    fixture.detectChanges();
  });

  it('should use the direct route ID and show loading feedback', () => {
    expect(getById).toHaveBeenCalledWith(inventoryItemId);
    expect(fixture.nativeElement.textContent).toContain('Loading inventory item...');
  });

  it('should render the returned inventory item', () => {
    response.next(item);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Lavender Essence');
    expect(fixture.nativeElement.textContent).toContain('Floral raw material');
    expect(fixture.nativeElement.textContent).toContain('Milliliter');
  });

  it('should render the mapped not-found state', () => {
    response.error(apiError(404, 'INVENTORY_ITEM_NOT_FOUND', 'Inventory item not found.'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Inventory item not found.');
  });

  it('should render a generic server error and retry', () => {
    response.error(apiError(500, 'INTERNAL_ERROR', 'Internal details'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'An unexpected server error occurred. Please try again.',
    );

    response = new Subject<InventoryItemDto>();
    getById.mockReturnValue(response);
    const retryButton = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (button) => (button as HTMLButtonElement).textContent?.trim() === 'Try again',
    ) as HTMLButtonElement | undefined;

    retryButton?.click();
    fixture.detectChanges();

    expect(getById).toHaveBeenCalledTimes(2);
    expect(getById).toHaveBeenLastCalledWith(inventoryItemId);
  });

  it('should keep the newest route item when route parameters change', () => {
    const firstResponse = response;
    const secondResponse = new Subject<InventoryItemDto>();
    const secondItem: InventoryItemDto = { ...item, id: 'second-item', name: 'Second item' };
    getById.mockReturnValueOnce(secondResponse);

    routeParams.next(convertToParamMap({ inventoryItemId: secondItem.id }));
    fixture.detectChanges();
    firstResponse.next(item);
    fixture.detectChanges();

    expect(getById).toHaveBeenLastCalledWith(secondItem.id);
    expect(fixture.nativeElement.textContent).toContain('Loading inventory item...');

    secondResponse.next(secondItem);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Second item');
    expect(fixture.nativeElement.textContent).not.toContain('Lavender Essence');
  });

  function apiError(status: number, code: string, message: string): HttpErrorResponse {
    return new HttpErrorResponse({
      status,
      error: {
        timestamp: '2026-09-01T12:00:00Z',
        status,
        error: status === 404 ? 'Not Found' : 'Internal Server Error',
        code,
        message,
        path: `/api/v1/inventory-items/${inventoryItemId}`,
        details: {},
      },
    });
  }
});
