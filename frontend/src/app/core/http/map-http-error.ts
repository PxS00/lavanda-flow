import { HttpErrorResponse } from '@angular/common/http';

import { ApiErrorResponse } from './api-error-response.dto';
import { UiError } from './ui-error';

export function mapHttpError(error: unknown): UiError {
  if (!(error instanceof HttpErrorResponse)) {
    return {
      kind: 'unknown',
      message: 'An unexpected error occurred.',
    };
  }

  if (error.status === 0) {
    return {
      kind: 'network',
      message: 'Unable to connect to the server.',
    };
  }

  return mapApiError(error);
}

function mapApiError(error: HttpErrorResponse): UiError {
  if (!isApiErrorResponse(error.error)) {
    return mapByStatus(error.status);
  }

  const apiError = error.error;

  switch (error.status) {
    case 400:
      return {
        kind: 'validation',
        message: apiError.message,
        code: apiError.code,
        ...(apiError.details === undefined || apiError.details === null
          ? {}
          : { fieldErrors: apiError.details }),
      };

    case 404:
      return {
        kind: 'not-found',
        message: apiError.message,
        code: apiError.code,
      };

    case 409:
      return {
        kind: 'conflict',
        message: apiError.message,
        code: apiError.code,
      };

    case 422:
      return {
        kind: 'unprocessable',
        message: apiError.message,
        code: apiError.code,
        ...(apiError.details === undefined || apiError.details === null
          ? {}
          : { fieldErrors: apiError.details }),
      };

    default:
      if (error.status >= 500) {
        return {
          kind: 'server',
          message: 'An unexpected server error occurred. Please try again.',
        };
      }

      return {
        kind: 'unknown',
        message: apiError.message,
        code: apiError.code,
      };
  }
}

function mapByStatus(status: number): UiError {
  switch (status) {
    case 400:
      return {
        kind: 'validation',
        message: 'The submitted data is invalid.',
      };

    case 404:
      return {
        kind: 'not-found',
        message: 'The requested resource was not found.',
      };

    case 409:
      return {
        kind: 'conflict',
        message: 'The request conflicts with the current state.',
      };

    case 422:
      return {
        kind: 'unprocessable',
        message: 'The request cannot be processed in the current resource state.',
      };

    default:
      if (status >= 500) {
        return {
          kind: 'server',
          message: 'An unexpected server error occurred. Please try again.',
        };
      }

      return {
        kind: 'unknown',
        message: 'An unexpected error occurred.',
      };
  }
}

function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (!isRecord(value)) {
    return false;
  }

  return (
    typeof value['timestamp'] === 'string' &&
    typeof value['status'] === 'number' &&
    typeof value['error'] === 'string' &&
    typeof value['code'] === 'string' &&
    typeof value['message'] === 'string' &&
    typeof value['path'] === 'string' &&
    (value['details'] === undefined || value['details'] === null || isStringRecord(value['details']))
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isStringRecord(value: unknown): value is Record<string, string> {
  if (!isRecord(value)) {
    return false;
  }

  return Object.values(value).every((entry) => typeof entry === 'string');
}
