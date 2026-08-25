import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';
import { AppError, toAppError } from '@/services/apiError';
import type { ApiError } from '@/types/api';

/** Builds an AxiosError shaped the way the interceptor will actually receive one. */
function axiosErrorWith(status: number, data: unknown): AxiosError {
  const error = new AxiosError('Request failed');
  error.response = {
    status,
    statusText: '',
    data,
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
  return error;
}

describe('toAppError', () => {
  it('prefers the backend message over a generic one', () => {
    // The backend's message is specific and is guaranteed to carry no stack trace,
    // SQL or constraint name, so it is the better thing to show a user.
    const payload: ApiError = {
      timestamp: '2026-08-24T09:15:30Z',
      status: 400,
      error: 'INVALID_TRANSACTION',
      message: "Category 'Salary' cannot be used for EXPENSE transactions",
      path: '/api/transactions',
    };

    const result = toAppError(axiosErrorWith(400, payload));

    expect(result.message).toBe("Category 'Salary' cannot be used for EXPENSE transactions");
    expect(result.code).toBe('INVALID_TRANSACTION');
    expect(result.status).toBe(400);
  });

  it('exposes validation failures keyed by field so a form can mark its inputs', () => {
    const payload: ApiError = {
      timestamp: '2026-08-24T09:15:30Z',
      status: 400,
      error: 'VALIDATION_FAILED',
      message: 'Request validation failed',
      path: '/api/transactions',
      fieldErrors: [
        { field: 'amount', message: 'Amount must be at least 0.01' },
        { field: 'transactionDate', message: 'Transaction date cannot be in the future' },
      ],
    };

    const result = toAppError(axiosErrorWith(400, payload));

    expect(result.isValidation).toBe(true);
    expect(result.fieldMap).toEqual({
      amount: 'Amount must be at least 0.01',
      transactionDate: 'Transaction date cannot be in the future',
    });
  });

  it('keeps the first message when one field has several violations', () => {
    const payload: ApiError = {
      timestamp: '2026-08-24T09:15:30Z',
      status: 400,
      error: 'VALIDATION_FAILED',
      message: 'Request validation failed',
      path: '/api/auth/register',
      fieldErrors: [
        { field: 'password', message: 'Password must be between 8 and 72 characters' },
        { field: 'password', message: 'Password must contain at least one letter and one digit' },
      ],
    };

    expect(toAppError(axiosErrorWith(400, payload)).fieldMap.password).toBe(
      'Password must be between 8 and 72 characters',
    );
  });

  it('falls back to a readable message when the body is not the standard shape', () => {
    // Covers a proxy or gateway answering before the request reaches the API.
    const result = toAppError(axiosErrorWith(502, '<html>Bad Gateway</html>'));

    expect(result.status).toBe(502);
    expect(result.code).toBe('UNKNOWN');
    expect(result.message).toMatch(/temporarily unavailable/i);
  });

  it('distinguishes an unreachable server from a server error', () => {
    // No response at all: the remedy is to check the connection, not to retry
    // later, so the two must not be collapsed into one message.
    const error = new AxiosError('Network Error');

    const result = toAppError(error);

    expect(result.status).toBe(0);
    expect(result.code).toBe('NETWORK_ERROR');
    expect(result.message).toMatch(/cannot reach/i);
  });

  it('reports a timeout distinctly', () => {
    const error = new AxiosError('timeout exceeded');
    error.code = 'ECONNABORTED';

    expect(toAppError(error).message).toMatch(/timed out/i);
  });

  it('passes an AppError through unchanged', () => {
    const original = new AppError('Already normalized', 404, 'RESOURCE_NOT_FOUND');
    expect(toAppError(original)).toBe(original);
  });

  it('never leaks internals for an unexpected throw', () => {
    const result = toAppError({ weird: 'object' });

    expect(result.code).toBe('UNKNOWN');
    expect(result.message).toBe('An unexpected error occurred.');
  });
});
