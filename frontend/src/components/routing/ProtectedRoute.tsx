import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { LoadingState } from '@/components/ui/Spinner';
import type { Role } from '@/types/api';

interface ProtectedRouteProps {
  /** When given, the caller must hold one of these roles to see the route. */
  roles?: Role[];
}

/**
 * Gates a route behind authentication, and optionally behind a role.
 *
 * **This is not security, and must never be mistaken for it.** Everything it does
 * happens in the browser, where a determined user can edit the state, replay the
 * bundle, or simply call the API directly with curl. Its entire purpose is to stop
 * an *ordinary* user landing on a screen that would only fail, and to avoid
 * rendering navigation to places they cannot use.
 *
 * The real enforcement is the backend: `@PreAuthorize` on the controllers, and
 * ownership checks in the service layer. Every route guarded here is guarded there
 * too, which is what makes bypassing this component pointless rather than
 * dangerous.
 */
export function ProtectedRoute({ roles }: ProtectedRouteProps) {
  const { isAuthenticated, initializing, user } = useAuth();
  const location = useLocation();

  // The stored token is still being verified. Redirecting now would bounce a
  // signed-in user to the login page on every refresh, which looks like a bug and
  // loses their place.
  if (initializing) {
    return <LoadingState label="Checking your session…" />;
  }

  if (!isAuthenticated) {
    // `state.from` lets the login page return the user to where they were headed,
    // and `replace` keeps the guarded URL out of history so Back does not bounce
    // between login and a page they still cannot reach.
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (roles && user && !roles.includes(user.role)) {
    // Signed in but not permitted: sent to a page explaining why, rather than to
    // login, which would wrongly imply different credentials would help.
    return <Navigate to="/forbidden" replace />;
  }

  return <Outlet />;
}

/**
 * Convenience wrapper for administrator-only sections.
 *
 * Note this only hides the UI. `GET /api/users` is independently restricted to
 * ADMIN by the backend, so a user who forces their way to the route sees a screen
 * whose every request comes back 403.
 */
export function AdminRoute() {
  return <ProtectedRoute roles={['ADMIN']} />;
}

/**
 * Keeps signed-in users away from the login and register pages.
 *
 * Without this, following a bookmark to /login while already authenticated shows
 * a form that, on submission, simply replaces a working session with itself.
 */
export function PublicOnlyRoute() {
  const { isAuthenticated, initializing } = useAuth();

  if (initializing) {
    return <LoadingState label="Loading…" />;
  }

  return isAuthenticated ? <Navigate to="/dashboard" replace /> : <Outlet />;
}
