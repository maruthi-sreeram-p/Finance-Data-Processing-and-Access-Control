import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminRoute, ProtectedRoute } from '@/components/routing/ProtectedRoute';
import { AuthProvider } from '@/context/AuthContext';
import { ToastProvider } from '@/context/ToastContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { userService } from '@/services/userService';
import { AppError } from '@/services/apiError';
import type { Role, UserResponse } from '@/types/api';

vi.mock('@/services/userService');

function userWithRole(role: Role): UserResponse {
  return {
    id: 1,
    name: 'Test User',
    email: 'test@fintrack.test',
    role,
    status: 'ACTIVE',
    createdAt: '2026-08-01T10:00:00Z',
    updatedAt: '2026-08-01T10:00:00Z',
  };
}

/** A syntactically valid JWT whose payload expires far in the future. */
function futureToken(): string {
  const payload = btoa(JSON.stringify({ sub: '1', exp: Math.floor(Date.now() / 1000) + 3600 }));
  return `header.${payload}.signature`;
}

function renderAt(route: string) {
  return render(
    <ThemeProvider>
      <ToastProvider>
        <MemoryRouter initialEntries={[route]}>
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<h1>Login page</h1>} />
              <Route path="/forbidden" element={<h1>Access denied</h1>} />

              <Route element={<ProtectedRoute />}>
                <Route path="/dashboard" element={<h1>Dashboard</h1>} />

                {/* ADMIN + MANAGER, matching the backend's guard on
                    GET /api/dashboard/system. */}
                <Route element={<ProtectedRoute roles={['ADMIN', 'MANAGER']} />}>
                  <Route path="/organization" element={<h1>Organization overview</h1>} />
                </Route>

                <Route element={<AdminRoute />}>
                  <Route path="/admin/users" element={<h1>User management</h1>} />
                </Route>
              </Route>
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </ToastProvider>
    </ThemeProvider>,
  );
}

/**
 * Route guarding.
 *
 * Worth restating what these tests do and do not prove: they show the client
 * routes people to sensible places. They prove nothing about security. Every route
 * below is independently authorized by the backend, and a user who edits state to
 * get past these guards reaches a page whose requests come back 401 or 403.
 */
describe('ProtectedRoute', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('sends an anonymous visitor to the login page', async () => {
    vi.mocked(userService.getCurrent).mockRejectedValue(new AppError('no session', 401, 'UNAUTHORIZED'));

    renderAt('/dashboard');

    expect(await screen.findByText('Login page')).toBeInTheDocument();
    expect(screen.queryByText('Dashboard')).not.toBeInTheDocument();
  });

  it('waits for the session check instead of redirecting mid-verification', () => {
    localStorage.setItem('fintrack.token', futureToken());
    // A request that never settles, standing in for one still in flight.
    vi.mocked(userService.getCurrent).mockReturnValue(new Promise<UserResponse>(() => {}));

    renderAt('/dashboard');

    // Redirecting here would bounce a signed-in user to login on every refresh.
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('Login page')).not.toBeInTheDocument();
  });

  it('admits an authenticated user', async () => {
    localStorage.setItem('fintrack.token', futureToken());
    vi.mocked(userService.getCurrent).mockResolvedValue(userWithRole('USER'));

    renderAt('/dashboard');

    expect(await screen.findByText('Dashboard')).toBeInTheDocument();
  });

  it('keeps a plain user out of the admin area', async () => {
    localStorage.setItem('fintrack.token', futureToken());
    vi.mocked(userService.getCurrent).mockResolvedValue(userWithRole('USER'));

    renderAt('/admin/users');

    // Sent to "access denied" rather than to login: different credentials would
    // not help, and implying otherwise is misleading.
    expect(await screen.findByText('Access denied')).toBeInTheDocument();
    expect(screen.queryByText('User management')).not.toBeInTheDocument();
  });

  it('keeps a manager out of the admin area', async () => {
    // Cross-user read privilege covers the ledger, not account administration.
    localStorage.setItem('fintrack.token', futureToken());
    vi.mocked(userService.getCurrent).mockResolvedValue(userWithRole('MANAGER'));

    renderAt('/admin/users');

    expect(await screen.findByText('Access denied')).toBeInTheDocument();
  });

  it('admits an administrator', async () => {
    localStorage.setItem('fintrack.token', futureToken());
    vi.mocked(userService.getCurrent).mockResolvedValue(userWithRole('ADMIN'));

    renderAt('/admin/users');

    expect(await screen.findByText('User management')).toBeInTheDocument();
  });

  it('admits both ADMIN and MANAGER to the organization overview', async () => {
    for (const role of ['ADMIN', 'MANAGER'] as const) {
      localStorage.setItem('fintrack.token', futureToken());
      vi.mocked(userService.getCurrent).mockResolvedValue(userWithRole(role));

      const { unmount } = renderAt('/organization');
      expect(await screen.findByText('Organization overview')).toBeInTheDocument();
      unmount();
    }
  });

  it('keeps a plain user out of the organization overview', async () => {
    // System-wide financial data is not something an ordinary account may read.
    // The backend returns 403 for the same request regardless of this guard.
    localStorage.setItem('fintrack.token', futureToken());
    vi.mocked(userService.getCurrent).mockResolvedValue(userWithRole('USER'));

    renderAt('/organization');

    expect(await screen.findByText('Access denied')).toBeInTheDocument();
    expect(screen.queryByText('Organization overview')).not.toBeInTheDocument();
  });

  it('still keeps a manager out of account administration', async () => {
    // Cross-user read privilege covers financial data, not user records -- the two
    // are separate grants and MANAGER holds only the first.
    localStorage.setItem('fintrack.token', futureToken());
    vi.mocked(userService.getCurrent).mockResolvedValue(userWithRole('MANAGER'));

    renderAt('/admin/users');

    expect(await screen.findByText('Access denied')).toBeInTheDocument();
  });

  it('treats a token the server rejects as no session at all', async () => {
    localStorage.setItem('fintrack.token', futureToken());
    vi.mocked(userService.getCurrent).mockRejectedValue(
      new AppError('Authentication is required', 401, 'UNAUTHORIZED'),
    );

    renderAt('/dashboard');

    // The account is fetched rather than decoded from the token, so a revoked or
    // suspended account is discovered immediately rather than at token expiry.
    expect(await screen.findByText('Login page')).toBeInTheDocument();
    expect(localStorage.getItem('fintrack.token')).toBeNull();
  });

  it('discards an already-expired token without calling the API', async () => {
    const expiredPayload = btoa(JSON.stringify({ sub: '1', exp: Math.floor(Date.now() / 1000) - 60 }));
    localStorage.setItem('fintrack.token', `header.${expiredPayload}.signature`);

    renderAt('/dashboard');

    expect(await screen.findByText('Login page')).toBeInTheDocument();
    expect(userService.getCurrent).not.toHaveBeenCalled();
  });
});
