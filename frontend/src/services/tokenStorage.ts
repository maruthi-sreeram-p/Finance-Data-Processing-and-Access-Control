/**
 * Persistence for the access token.
 *
 * ## Why localStorage, and what it costs
 *
 * The honest tradeoff, stated rather than glossed over: a token in
 * `localStorage` is readable by any JavaScript running on the page, so a
 * cross-site scripting flaw becomes a full account takeover. The alternative
 * usually recommended -- an httpOnly, SameSite cookie -- is not reachable here,
 * because it requires the backend to set and refresh the cookie, and FinTrack's
 * API is deliberately stateless and issues a single bearer token with no refresh
 * endpoint.
 *
 * Holding the token only in memory would close the XSS hole but log the user out
 * on every page reload, which is not a reasonable trade for an application people
 * keep open in a tab.
 *
 * What makes the exposure bounded rather than open-ended:
 *  - tokens expire in one hour, so a stolen one has a short life;
 *  - the backend re-reads the account on every request, so suspending an account
 *    invalidates its live tokens immediately rather than at expiry;
 *  - React escapes interpolated content by default, and the application renders
 *    no user content as raw HTML anywhere.
 *
 * The documented upgrade path is a refresh-token endpoint plus httpOnly cookies;
 * it is listed in the README as a future improvement rather than pretended away.
 */

const TOKEN_KEY = 'fintrack.token';

/**
 * Guards against storage being unavailable.
 *
 * `localStorage` throws rather than returning null in Safari private browsing and
 * when a browser blocks third-party storage. An auth layer that crashes on read
 * would make the whole application fail to boot, so every access is wrapped.
 */
function safeRead(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeWrite(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // Storage unavailable or full. The session still works for this page load;
    // it simply will not survive a reload.
  }
}

function safeRemove(key: string): void {
  try {
    window.localStorage.removeItem(key);
  } catch {
    // Nothing useful to do -- the caller is already logging out.
  }
}

export const tokenStorage = {
  get(): string | null {
    return safeRead(TOKEN_KEY);
  },

  set(token: string): void {
    safeWrite(TOKEN_KEY, token);
  },

  clear(): void {
    safeRemove(TOKEN_KEY);
  },
};

/**
 * Reads the expiry out of a JWT without verifying it.
 *
 * Verification is the server's job and cannot be done in a browser anyway -- the
 * signing key is secret. This exists only so the client can avoid sending a token
 * it already knows is stale, saving a pointless round trip and a jarring
 * redirect. A tampered token would sail past this check and be rejected by the
 * backend, which is the correct division of responsibility.
 *
 * @returns expiry as a millisecond timestamp, or null if unreadable
 */
export function readTokenExpiry(token: string): number | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) {
      return null;
    }

    // JWTs use base64url; atob expects standard base64.
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=');

    const claims = JSON.parse(atob(padded)) as { exp?: number };
    // `exp` is in seconds; the rest of the client works in milliseconds.
    return typeof claims.exp === 'number' ? claims.exp * 1000 : null;
  } catch {
    return null;
  }
}

/**
 * @param token the JWT to inspect
 * @param skewMs treat a token expiring within this window as already expired, so
 *        a request is not sent moments before it would be rejected
 */
export function isTokenExpired(token: string, skewMs = 5_000): boolean {
  const expiry = readTokenExpiry(token);
  // Unreadable expiry: let the backend decide rather than locking the user out
  // over a parsing quirk.
  return expiry === null ? false : Date.now() + skewMs >= expiry;
}
