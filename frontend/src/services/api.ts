import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import { toAppError } from './apiError';
import { isTokenExpired, tokenStorage } from './tokenStorage';

/**
 * The single HTTP client for the whole application.
 *
 * Everything that must happen on every request -- attaching the bearer token,
 * normalizing errors, reacting to an expired session -- happens here, once. The
 * alternative, an `axios.get` scattered through components, means the day the
 * auth header changes you have to find every call site and hope you found them
 * all.
 */

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

export const api: AxiosInstance = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
  // Long enough for a cold JVM to answer the first request, short enough that a
  // dead backend surfaces as an error rather than a spinner that never resolves.
  timeout: 20_000,
});

// --- Session expiry --------------------------------------------------------

type SessionExpiredHandler = () => void;

let onSessionExpired: SessionExpiredHandler | null = null;

/**
 * Registers what should happen when the session ends -- clearing auth state and
 * redirecting to login.
 *
 * Injected by the auth provider rather than imported, because this module must
 * not depend on React or on the router. Calling `window.location` from here would
 * work but would throw away the router's state and full-page-reload the app.
 */
export function setSessionExpiredHandler(handler: SessionExpiredHandler | null): void {
  onSessionExpired = handler;
}

/** Requests that legitimately have no token and must not trigger a logout on 401. */
const PUBLIC_PATHS = ['/auth/login', '/auth/register'];

function isPublicRequest(url: string | undefined): boolean {
  if (!url) {
    return false;
  }
  return PUBLIC_PATHS.some((path) => url.includes(path));
}

// --- Request: attach the token --------------------------------------------

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (isPublicRequest(config.url)) {
    return config;
  }

  const token = tokenStorage.get();
  if (!token) {
    return config;
  }

  // Checked client-side purely to avoid a guaranteed-doomed round trip. The
  // backend still validates every token it receives; this is an optimisation,
  // never a security decision.
  if (isTokenExpired(token)) {
    tokenStorage.clear();
    onSessionExpired?.();
    return config;
  }

  config.headers.set('Authorization', `Bearer ${token}`);
  return config;
});

// --- Response: normalize errors, handle expiry -----------------------------

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const appError = toAppError(error);

    // A 401 on an authenticated request means the token is gone, expired or
    // belongs to an account that has since been suspended -- the backend re-reads
    // the account on every request, so suspension takes effect immediately.
    //
    // Login and register are excluded: a 401 there means "wrong password", and
    // wiping state and redirecting would bounce the user off the very form they
    // are trying to use.
    const config = axios.isAxiosError(error) ? error.config : undefined;
    if (appError.status === 401 && !isPublicRequest(config?.url)) {
      tokenStorage.clear();
      onSessionExpired?.();
    }

    // Rejecting with AppError means every caller -- service, hook or component --
    // receives the same normalized type, and `axios` never escapes this module.
    return Promise.reject(appError);
  },
);

/**
 * Serializes query parameters, dropping anything empty.
 *
 * Sending `?type=` with no value would be bound by Spring as an empty string and
 * fail enum conversion with a 400, so an untouched filter must be omitted from
 * the URL entirely rather than sent blank.
 */
export function buildParams(source: Record<string, unknown>): URLSearchParams {
  const params = new URLSearchParams();

  for (const [key, value] of Object.entries(source)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    params.append(key, String(value));
  }

  return params;
}
