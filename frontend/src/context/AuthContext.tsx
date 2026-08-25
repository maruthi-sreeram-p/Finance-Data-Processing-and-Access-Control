import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { authService } from '@/services/authService';
import { userService } from '@/services/userService';
import { setSessionExpiredHandler } from '@/services/api';
import { isTokenExpired, tokenStorage } from '@/services/tokenStorage';
import type { LoginRequest, RegisterRequest, Role, UserResponse } from '@/types/api';

export interface AuthContextValue {
  /** The signed-in account, or null when anonymous. */
  user: UserResponse | null;
  /** False while the stored token is being verified on first load. */
  initializing: boolean;
  isAuthenticated: boolean;
  login: (request: LoginRequest) => Promise<UserResponse>;
  register: (request: RegisterRequest) => Promise<UserResponse>;
  logout: () => void;
  /** Refreshes the cached account after a profile edit. */
  refreshUser: () => Promise<void>;
  /** Convenience for conditional navigation. Never a substitute for backend checks. */
  hasRole: (...roles: Role[]) => boolean;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

interface AuthProviderProps {
  children: ReactNode;
}

/**
 * Holds the authentication state for the application.
 *
 * Two points worth being explicit about:
 *
 * 1. **The user object is fetched, not decoded.** A JWT carries `email` and
 *    `role` claims, and reading them would save a request -- but a token is a
 *    snapshot from the moment it was issued. If an administrator changed the role
 *    since, the claim is stale. `GET /api/users/me` returns what the account
 *    actually is right now, which also means a suspended account is discovered
 *    immediately rather than at expiry.
 *
 * 2. **None of this is security.** `hasRole` decides what to render; it decides
 *    nothing about what the caller may do. Every protected operation is authorized
 *    again by the backend, which is why hiding an admin link is a courtesy and not
 *    a control.
 */
export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [initializing, setInitializing] = useState(true);

  const logout = useCallback(() => {
    tokenStorage.clear();
    setUser(null);
  }, []);

  /**
   * Restores the session on first load.
   *
   * A token in storage is treated as a claim to verify, not a fact: the account is
   * fetched from the API, and any failure -- expired, revoked, suspended, backend
   * unreachable -- results in a clean logged-out state rather than a half-signed-in
   * application that fails on its first real request.
   */
  useEffect(() => {
    let cancelled = false;

    async function restoreSession() {
      const token = tokenStorage.get();

      if (!token || isTokenExpired(token)) {
        tokenStorage.clear();
        if (!cancelled) {
          setInitializing(false);
        }
        return;
      }

      try {
        const current = await userService.getCurrent();
        if (!cancelled) {
          setUser(current);
        }
      } catch {
        // The interceptor has already cleared the token on a 401. Clearing again
        // is harmless and also covers the non-401 failure paths.
        tokenStorage.clear();
        if (!cancelled) {
          setUser(null);
        }
      } finally {
        if (!cancelled) {
          setInitializing(false);
        }
      }
    }

    void restoreSession();

    // Guards against setting state after unmount, which React 18 strict mode makes
    // easy to hit by double-invoking effects in development.
    return () => {
      cancelled = true;
    };
  }, []);

  /**
   * Lets the HTTP layer end the session without importing React.
   *
   * The interceptor detects a 401 and needs the UI to react; wiring it through
   * here keeps that dependency pointing one way, from React to the API client.
   */
  useEffect(() => {
    setSessionExpiredHandler(() => setUser(null));
    return () => setSessionExpiredHandler(null);
  }, []);

  const login = useCallback(async (request: LoginRequest) => {
    const response = await authService.login(request);
    tokenStorage.set(response.accessToken);
    setUser(response.user);
    return response.user;
  }, []);

  const register = useCallback(async (request: RegisterRequest) => {
    // Registration returns a token as well as the account, so a new user lands
    // signed in rather than being bounced to a login form they just filled in.
    const response = await authService.register(request);
    tokenStorage.set(response.accessToken);
    setUser(response.user);
    return response.user;
  }, []);

  const refreshUser = useCallback(async () => {
    const current = await userService.getCurrent();
    setUser(current);
  }, []);

  const hasRole = useCallback(
    (...roles: Role[]) => (user ? roles.includes(user.role) : false),
    [user],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      initializing,
      isAuthenticated: user !== null,
      login,
      register,
      logout,
      refreshUser,
      hasRole,
    }),
    [user, initializing, login, register, logout, refreshUser, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
