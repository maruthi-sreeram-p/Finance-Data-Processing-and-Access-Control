import { api } from './api';
import type { AuthResponse, LoginRequest, RegisterRequest } from '@/types/api';

/**
 * Registration and login.
 *
 * Note what is absent: no token handling. Persisting the token and updating
 * application state is the auth context's job, so this module stays a thin,
 * independently testable description of two endpoints.
 */
export const authService = {
  /** `POST /api/auth/register` -- always creates a USER; the role cannot be chosen. */
  async register(request: RegisterRequest): Promise<AuthResponse> {
    const { data } = await api.post<AuthResponse>('/auth/register', request);
    return data;
  },

  /** `POST /api/auth/login` -- every failure returns the same 401, by design. */
  async login(request: LoginRequest): Promise<AuthResponse> {
    const { data } = await api.post<AuthResponse>('/auth/login', request);
    return data;
  },
};
