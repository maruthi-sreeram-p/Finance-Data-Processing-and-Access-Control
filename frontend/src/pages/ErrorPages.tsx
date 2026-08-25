import { Link, useNavigate } from 'react-router-dom';
import styles from './ErrorPages.module.css';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/hooks/useAuth';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';

/**
 * Catch-all for an unmatched route.
 *
 * The destination of the primary action depends on whether there is a session, so
 * "go home" lands somewhere useful rather than bouncing a signed-in user through
 * the marketing page.
 */
export function NotFoundPage() {
  useDocumentTitle('Page not found');

  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  return (
    <div className={styles.page}>
      <div className={styles.inner}>
        <p className={styles.code}>404</p>
        <h1 className={styles.title}>We could not find that page</h1>
        <p className={styles.copy}>
          The link may be out of date, or the page may have been moved.
        </p>
        <div className={styles.actions}>
          <Button variant="secondary" onClick={() => navigate(-1)}>
            Go back
          </Button>
          <Link to={isAuthenticated ? '/dashboard' : '/'}>
            <Button>{isAuthenticated ? 'Go to dashboard' : 'Go to home'}</Button>
          </Link>
        </div>
      </div>
    </div>
  );
}

/**
 * Shown when a signed-in user reaches a route their role does not permit.
 *
 * Distinct from the login redirect on purpose: sending them to a login form would
 * imply that different credentials would help, when the account simply lacks the
 * privilege. The backend independently refuses the same request, so arriving here
 * is a navigation outcome rather than a security boundary.
 */
export function ForbiddenPage() {
  useDocumentTitle('Access denied');

  const { user } = useAuth();

  return (
    <div className={styles.page}>
      <div className={styles.inner}>
        <p className={styles.code}>403</p>
        <h1 className={styles.title}>You do not have access to this page</h1>
        <p className={styles.copy}>
          {user
            ? `Your account has the ${user.role.toLowerCase()} role, which does not include this area. Contact an administrator if you believe this is wrong.`
            : 'This area requires additional permissions.'}
        </p>
        <div className={styles.actions}>
          <Link to="/dashboard">
            <Button>Back to dashboard</Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
