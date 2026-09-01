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
        details: {
          name: 'must not be blank',
        },
      },
    });

    const result = mapHttpError(error);

    expect(result).toEqual({
      kind: 'validation',
      message: 'Request validation failed.',
      code: 'VALIDATION_ERROR',
      fieldErrors: {
        name: 'must not be blank',
      },
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

    const result = mapHttpError(error);

    expect(result).toEqual({
      kind: 'not-found',
      message: 'Supplier not found.',
      code: 'SUPPLIER_NOT_FOUND',
    });
  });

  it('should fall back to the HTTP status when the backend payload is invalid', () => {
    const error = new HttpErrorResponse({
      status: 404,
      statusText: 'Not Found',
      error: {
        message: 'Malformed response',
      },
    });

    const result = mapHttpError(error);

    expect(result).toEqual({
      kind: 'not-found',
      message: 'The requested resource was not found.',
    });
  });

  it('should map a backend conflict error', () => {
    const error = new HttpErrorResponse({
      status: 409,
      statusText: 'Conflict',
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

    const result = mapHttpError(error);

    expect(result).toEqual({
      kind: 'conflict',
      message: 'Supplier already exists.',
      code: 'SUPPLIER_ALREADY_EXISTS',
    });
  });

  it('should hide backend details for server errors', () => {
    const error = new HttpErrorResponse({
      status: 500,
      statusText: 'Internal Server Error',
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

    const result = mapHttpError(error);

    expect(result).toEqual({
      kind: 'server',
      message: 'An unexpected server error occurred. Please try again.',
    });
  });
});
