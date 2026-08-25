import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import styles from './AuthPages.module.css';
import { AuthShell } from './AuthShell';
import { TextField } from '@/components/ui/Field';
import { Button } from '@/components/ui/Button';
import { ErrorMessage } from '@/components/ui/States';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/useToast';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { toAppError } from '@/services/apiError';

interface LocationState {
  from?: { pathname: string };
}

export function LoginPage() {
  useDocumentTitle('Log in');

  const { login } = useAuth();
  const { showSuccess } = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    setFieldErrors({});
    setSubmitting(true);

    try {
      const user = await login({ email: email.trim(), password });

      showSuccess(`Welcome back, ${user.name.split(' ')[0]}.`);

      // Return the user to whatever they were trying to reach before the guard
      // intercepted them, rather than always dumping them on the dashboard.
      const from = (location.state as LocationState | null)?.from?.pathname;
      navigate(from && from !== '/login' ? from : '/dashboard', { replace: true });
    } catch (caught) {
      const error = toAppError(caught);

      // The backend answers every login failure with one identical 401 -- wrong
      // password, unknown email, suspended account all look the same, so the
      // endpoint cannot be used to discover which addresses are registered. The
      // UI must not undo that by guessing at a more specific message.
      setFormError(error.message);
      setFieldErrors(error.fieldMap);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthShell
      title="Log in"
      subtitle="Enter your details to reach your dashboard."
      footer={
        <>
          Don&apos;t have an account? <Link to="/register">Create one</Link>
        </>
      }
    >
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        {formError && <ErrorMessage message={formError} />}

        <TextField
          label="Email"
          type="email"
          name="email"
          // Helps password managers offer the right credentials, and phones show
          // the email keyboard.
          autoComplete="email"
          inputMode="email"
          autoFocus
          required
          value={email}
          error={fieldErrors.email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="you@example.com"
        />

        <TextField
          label="Password"
          type="password"
          name="password"
          autoComplete="current-password"
          required
          value={password}
          error={fieldErrors.password}
          onChange={(event) => setPassword(event.target.value)}
          placeholder="Your password"
        />

        <Button type="submit" size="lg" fullWidth loading={submitting}>
          Log in
        </Button>
      </form>
    </AuthShell>
  );
}
