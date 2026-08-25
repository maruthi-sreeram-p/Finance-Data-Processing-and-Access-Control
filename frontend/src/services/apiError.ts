import axios from 'axios';
import type { ApiError, ApiErrorCode, ApiFieldError } from '@/types/api';

/**
 * A backend failure, normalized into something the UI can render.
 *
 * Every network and HTTP failure in the application becomes one of these, so a
 * component never has to know whether it is holding an Axios error, a
 * `FinTrackException` payload, or a dropped connection.
 */
export class AppError extends Error {
  readonly status: number;
  readonly code: ApiErrorCode | 'NETWORK_ERROR' | 'UNKNOWN';
  readonly fieldErrors: ApiFieldError[];

  constructor(
    message: string,
    status: number,
    code: AppError['code'],
    fieldErrors: ApiFieldError[] = [],
  ) {
    super(message);
    this.name = 'AppError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }

  /** True when the backend rejected specific fields, so a form can mark them. */
  get isValidation(): boolean {
    return this.fieldErrors.length > 0;
  }

  /**
   * Field errors keyed by property name, for a form to look up per input.
   * The backend reports every rejected field at once, so a form can show all of
   * its errors after a single submission rather than one per round trip.
   */
  get fieldMap(): Record<string, string> {
    const map: Record<string, string> = {};
    for (const item of this.fieldErrors) {
      // First message wins: a field with two violations shows the first, rather
      // than flickering between them.
      if (!(item.field in map)) {
        map[item.field] = item.message;
      }
    }
    return map;
  }
}

/**
 * Messages shown when the backend gives no better one.
 *
 * Deliberately plain and actionable. The backend's own `message` is used when
 * present -- it is written to be safe for end users and never contains internals
 * -- and these are the fallback for transport failures and unexpected statuses.
 */
const FALLBACK_MESSAGES: Record<number, string> = {
  400: 'Please check the information you entered.',
  401: 'Your session has expired. Please log in again.',
  403: 'You do not have permission to perform this action.',
  404: 'We could not find what you were looking for.',
  409: 'That conflicts with information that already exists.',
  422: 'Please check the information you entered.',
  429: 'Too many requests. Please wait a moment and try again.',
  500: 'Something went wrong on our end. Please try again.',
  502: 'The service is temporarily unavailable. Please try again.',
  503: 'The service is temporarily unavailable. Please try again.',
};

const NETWORK_MESSAGE =
  'Cannot reach the FinTrack server. Check your connection and make sure the API is running.';

/** Narrows an unknown payload to the backend's error shape. */
function isApiError(payload: unknown): payload is ApiError {
  if (typeof payload !== 'object' || payload === null) {
    return false;
  }
  const candidate = payload as Partial<ApiError>;
  return typeof candidate.error === 'string' && typeof candidate.status === 'number';
}

/**
 * Converts anything thrown by the HTTP layer into an {@link AppError}.
 *
 * This is the only place in the application that inspects an Axios error, which
 * is what keeps `axios` an implementation detail of the service layer rather than
 * a type that leaks into components.
 */
export function toAppError(error: unknown): AppError {
  if (error instanceof AppError) {
    return error;
  }

  if (axios.isAxiosError(error)) {
    // No response at all: the request never completed. Distinguished from a 5xx
    // because the remedy is different -- check the connection, not retry later.
    if (!error.response) {
      const message =
        error.code === 'ECONNABORTED'
          ? 'The request timed out. Please try again.'
          : NETWORK_MESSAGE;
      return new AppError(message, 0, 'NETWORK_ERROR');
    }

    const { status, data } = error.response;

    if (isApiError(data)) {
      // The backend's message is preferred: it is specific ("Category 'Salary'
      // cannot be used for EXPENSE transactions") and is guaranteed to carry no
      // stack trace, SQL or constraint name.
      return new AppError(
        data.message || FALLBACK_MESSAGES[status] || 'Something went wrong.',
        status,
        data.error,
        data.fieldErrors ?? [],
      );
    }

    return new AppError(
      FALLBACK_MESSAGES[status] ?? 'Something went wrong. Please try again.',
      status,
      'UNKNOWN',
    );
  }

  if (error instanceof Error) {
    return new AppError(error.message, 0, 'UNKNOWN');
  }

  return new AppError('An unexpected error occurred.', 0, 'UNKNOWN');
}
