import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginPage } from '@/pages/LoginPage';
import { AuthProvider } from '@/context/AuthContext';
import { ToastProvider } from '@/context/ToastContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { authService } from '@/services/authService';
import { userService } from '@/services/userService';
import { AppError } from '@/services/apiError';
import type { AuthResponse } from '@/types/api';

// The services are mocked, not axios: that keeps these tests about the component's
// behaviour rather than about HTTP wiring, which the backend integration suite
// already covers end to end.
vi.mock('@/services/authService');
vi.mock('@/services/userService');

const AUTH_RESPONSE: AuthResponse = {
  accessToken: 'header.payload.signature',
  tokenType: 'Bearer',
  expiresIn: 3600,
  user: {
    id: 1,
    name: 'Priya Raman',
    email: 'priya@fintrack.test',
    role: 'USER',
    status: 'ACTIVE',
    createdAt: '2026-08-01T10:00:00Z',
    updatedAt: '2026-08-01T10:00:00Z',
  },
};

function renderLogin() {
  return render(
    <ThemeProvider>
      <ToastProvider>
        <MemoryRouter initialEntries={['/login']}>
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/dashboard" element={<h1>Dashboard reached</h1>} />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </ToastProvider>
    </ThemeProvider>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    // No stored token, so the provider settles straight to logged-out.
    vi.mocked(userService.getCurrent).mockRejectedValue(new AppError('no session', 401, 'UNAUTHORIZED'));
  });

  it('associates every input with a label so the form is usable by name', async () => {
    renderLogin();

    // getByLabelText only succeeds when label and input are properly associated --
    // a visually adjacent label that is not linked would fail here, exactly as it
    // would fail for a screen reader.
    expect(await screen.findByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument();
  });

  it('submits the typed credentials and navigates to the dashboard', async () => {
    vi.mocked(authService.login).mockResolvedValue(AUTH_RESPONSE);
    const user = userEvent.setup();

    renderLogin();

    await user.type(await screen.findByLabelText(/email/i), 'priya@fintrack.test');
    await user.type(screen.getByLabelText(/password/i), 'Passw0rd123');
    await user.click(screen.getByRole('button', { name: /log in/i }));

    await waitFor(() => {
      expect(authService.login).toHaveBeenCalledWith({
        email: 'priya@fintrack.test',
        password: 'Passw0rd123',
      });
    });

    expect(await screen.findByText('Dashboard reached')).toBeInTheDocument();
  });

  it('stores the token so the session survives a reload', async () => {
    vi.mocked(authService.login).mockResolvedValue(AUTH_RESPONSE);
    const user = userEvent.setup();

    renderLogin();

    await user.type(await screen.findByLabelText(/email/i), 'priya@fintrack.test');
    await user.type(screen.getByLabelText(/password/i), 'Passw0rd123');
    await user.click(screen.getByRole('button', { name: /log in/i }));

    await waitFor(() => {
      expect(localStorage.getItem('fintrack.token')).toBe('header.payload.signature');
    });
  });

  it('shows the backend message on failure and stays on the form', async () => {
    // The backend answers every login failure with one identical 401, so the UI
    // must not embellish it into something more specific.
    vi.mocked(authService.login).mockRejectedValue(
      new AppError('Invalid email or password', 401, 'UNAUTHORIZED'),
    );
    const user = userEvent.setup();

    renderLogin();

    await user.type(await screen.findByLabelText(/email/i), 'priya@fintrack.test');
    await user.type(screen.getByLabelText(/password/i), 'WrongPassword1');
    await user.click(screen.getByRole('button', { name: /log in/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
    expect(screen.queryByText('Dashboard reached')).not.toBeInTheDocument();
    expect(localStorage.getItem('fintrack.token')).toBeNull();
  });

  it('never writes the password anywhere it could persist', async () => {
    vi.mocked(authService.login).mockResolvedValue(AUTH_RESPONSE);
    const user = userEvent.setup();

    renderLogin();

    await user.type(await screen.findByLabelText(/email/i), 'priya@fintrack.test');
    await user.type(screen.getByLabelText(/password/i), 'Passw0rd123');
    await user.click(screen.getByRole('button', { name: /log in/i }));

    await waitFor(() => expect(authService.login).toHaveBeenCalled());

    const stored = JSON.stringify(localStorage);
    expect(stored).not.toContain('Passw0rd123');
  });

  it('disables the submit button while the request is in flight', async () => {
    // Guards against a double click creating two sessions, or two records on the
    // forms that create things.
    let resolveLogin: (value: AuthResponse) => void = () => {};
    vi.mocked(authService.login).mockReturnValue(
      new Promise<AuthResponse>((resolve) => {
        resolveLogin = resolve;
      }),
    );
    const user = userEvent.setup();

    renderLogin();

    await user.type(await screen.findByLabelText(/email/i), 'priya@fintrack.test');
    await user.type(screen.getByLabelText(/password/i), 'Passw0rd123');

    const submit = screen.getByRole('button', { name: /log in/i });
    await user.click(submit);

    await waitFor(() => expect(submit).toBeDisabled());

    resolveLogin(AUTH_RESPONSE);
  });
});
