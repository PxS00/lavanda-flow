import { HttpErrorResponse } from '@angular/common/http';

import { mapHttpError } from './map-http-error';

describe('mapHttpError', () => {
  it('should map a non-http error to unknown', () => {
    const result = mapHttpError(new Error('Unexpected'));

    expect(result).toEqual({
      kind: 'unknown',
      message: 'An unexpected error occurred.',
    });
  });

  it('should map status 0 to a network error', () => {
    const error = new HttpErrorResponse({
      status: 0,
      statusText: 'Unknown Error',
      error: new Error('Network failure'),
    });

    const result = mapHttpError(error);

    expect(result).toEqual({
      kind: 'network',
      message: 'Unable to connect to the server.',
    });
  });

  it('should map a backend validation error', () => {
    const error = new HttpErrorResponse({
      status: 400,
      statusText: 'Bad Request',
      url: '/api/v1/suppliers',
      error: {
        timestamp: '2026-08-31T23:30:00Z',
        status: 400,
        error: 'Bad Request',
        code: 'VALIDATION_ERROR',
        message: 'Request validation failed.',
        path: '/api/v1/suppliers',
        details: { name: 'must not be blank' },
      },
    });

    expect(mapHttpError(error)).toEqual({
      kind: 'validation',
      message: 'Request validation failed.',
      code: 'VALIDATION_ERROR',
      details: { name: 'must not be blank' },
    });
  });

  it('should map a backend not-found error', () => {
    const error = new HttpErrorResponse({
      status: 404,
      statusText: 'Not Found',
      error: {
        timestamp: '2026-08-31T23:30:00Z',
        status: 404,
        error: 'Not Found',
        code: 'SUPPLIER_NOT_FOUND',
        message: 'Supplier not found.',
        path: '/api/v1/suppliers/123',
        details: {},
      },
    });

    expect(mapHttpError(error)).toEqual({
      kind: 'not-found',
      message: 'Supplier not found.',
      code: 'SUPPLIER_NOT_FOUND',
    });
  });

  it('should preserve a valid not-found API error when details are absent', () => {
    const error = new HttpErrorResponse({
      status: 404,
      error: {
        timestamp: '2026-09-01T20:30:00Z',
        status: 404,
        error: 'Not Found',
        code: 'INVENTORY_ITEM_NOT_FOUND',
        message: 'Inventory item not found: 123',
        path: '/api/v1/inventory/items/123/withdrawals',
      },
    });

    expect(mapHttpError(error)).toEqual({
      kind: 'not-found',
      code: 'INVENTORY_ITEM_NOT_FOUND',
      message: 'Inventory item not found: 123',
    });
  });

  it('should fall back to the HTTP status when the backend payload is invalid', () => {
    const error = new HttpErrorResponse({ status: 404, error: { message: 'Malformed response' } });

    expect(mapHttpError(error)).toEqual({
      kind: 'not-found',
      message: 'The requested resource was not found.',
    });
  });

  it('should map a backend conflict error', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: {
        timestamp: '2026-08-31T23:30:00Z',
        status: 409,
        error: 'Conflict',
        code: 'SUPPLIER_ALREADY_EXISTS',
        message: 'Supplier already exists.',
        path: '/api/v1/suppliers',
        details: {},
      },
    });

    expect(mapHttpError(error)).toEqual({
      kind: 'conflict',
      message: 'Supplier already exists.',
      code: 'SUPPLIER_ALREADY_EXISTS',
    });
  });

  it('should map an unprocessable backend error with actionable details', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: {
        timestamp: '2026-09-01T20:30:00Z',
        status: 422,
        error: 'Unprocessable Content',
        code: 'INVENTORY_ITEM_INACTIVE',
        message: 'Inventory item must be active to receive stock.',
        path: '/api/v1/inventory/receipts',
        details: {},
      },
    });

    expect(mapHttpError(error)).toEqual({
      kind: 'unprocessable',
      message: 'Inventory item must be active to receive stock.',
      code: 'INVENTORY_ITEM_INACTIVE',
      details: {},
    });
  });

  it('should preserve a valid unprocessable API error when details are null', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: {
        timestamp: '2026-09-01T20:30:00Z',
        status: 422,
        error: 'Unprocessable Content',
        code: 'INACTIVE_INVENTORY_ITEM',
        message: 'Inventory item is inactive: 123',
        path: '/api/v1/inventory/items/123/withdrawals',
        details: null,
      },
    });

    expect(mapHttpError(error)).toEqual({
      kind: 'unprocessable',
      code: 'INACTIVE_INVENTORY_ITEM',
      message: 'Inventory item is inactive: 123',
    });
  });

  it('should preserve insufficient-stock details from a valid unprocessable API error', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: {
        timestamp: '2026-09-01T20:30:00Z',
        status: 422,
        error: 'Unprocessable Content',
        code: 'INSUFFICIENT_ELIGIBLE_STOCK',
        message: 'Insufficient eligible stock for inventory item 123',
        path: '/api/v1/inventory/items/123/withdrawals',
        details: { availableQuantity: '55.000000' },
      },
    });

    expect(mapHttpError(error)).toEqual({
      kind: 'unprocessable',
      code: 'INSUFFICIENT_ELIGIBLE_STOCK',
      message: 'Insufficient eligible stock for inventory item 123',
      details: { availableQuantity: '55.000000' },
    });
  });

  it('should reject an API error with array details', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: {
        timestamp: '2026-09-01T20:30:00Z',
        status: 422,
        error: 'Unprocessable Content',
        code: 'INSUFFICIENT_ELIGIBLE_STOCK',
        message: 'Insufficient eligible stock.',
        path: '/api/v1/inventory/items/123/withdrawals',
        details: ['availableQuantity'],
      },
    });

    expect(mapHttpError(error)).toEqual({
      kind: 'unprocessable',
      message: 'The request cannot be processed in the current resource state.',
    });
  });

  it('should reject an API error with malformed non-string details', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: {
        timestamp: '2026-09-01T20:30:00Z',
        status: 422,
        error: 'Unprocessable Content',
        code: 'INSUFFICIENT_ELIGIBLE_STOCK',
        message: 'Insufficient eligible stock.',
        path: '/api/v1/inventory/items/123/withdrawals',
        details: { availableQuantity: 55 },
      },
    });

    expect(mapHttpError(error)).toEqual({
      kind: 'unprocessable',
      message: 'The request cannot be processed in the current resource state.',
    });
  });

  it('should hide backend details for server errors', () => {
    const error = new HttpErrorResponse({
      status: 500,
      error: {
        timestamp: '2026-08-31T23:30:00Z',
        status: 500,
        error: 'Internal Server Error',
        code: 'INTERNAL_ERROR',
        message: 'Sensitive internal database information',
        path: '/api/v1/suppliers',
        details: {},
      },
    });

    expect(mapHttpError(error)).toEqual({
      kind: 'server',
      message: 'An unexpected server error occurred. Please try again.',
    });
  });
});
