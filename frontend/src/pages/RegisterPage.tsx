import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import styles from './AuthPages.module.css';
import { AuthShell } from './AuthShell';
import { TextField } from '@/components/ui/Field';
import { Button } from '@/components/ui/Button';
import { ErrorMessage } from '@/components/ui/States';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/useToast';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { toAppError } from '@/services/apiError';

/**
 * Client-side validation.
 *
 * These rules mirror the backend's Bean Validation constraints exactly, and they
 * exist purely to save a round trip and give immediate feedback. The server
 * re-validates everything regardless — this is a convenience, not a control, and
 * a mismatch here can only ever produce a slightly slower rejection, never an
 * accepted bad value.
 */
function validate(values: {
  name: string;
  email: string;
  password: string;
  confirmPassword: string;
}): Record<string, string> {
  const errors: Record<string, string> = {};

  if (!values.name.trim()) {
    errors.name = 'Name is required';
  } else if (values.name.trim().length < 2 || values.name.trim().length > 100) {
    errors.name = 'Name must be between 2 and 100 characters';
  }

  if (!values.email.trim()) {
    errors.email = 'Email is required';
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email.trim())) {
    errors.email = 'Enter a valid email address';
  }

  if (!values.password) {
    errors.password = 'Password is required';
  } else if (values.password.length < 8 || values.password.length > 72) {
    // The 72 ceiling is BCrypt's: it ignores anything beyond 72 bytes, so a
    // longer password would be interchangeable with its own truncation.
    errors.password = 'Password must be between 8 and 72 characters';
  } else if (!/[A-Za-z]/.test(values.password) || !/[0-9]/.test(values.password)) {
    errors.password = 'Password must contain at least one letter and one digit';
  }

  // Confirmation is a client-only field: the backend never sees it, and its whole
  // purpose is catching a typo before it becomes an account nobody can log into.
  if (values.confirmPassword !== values.password) {
    errors.confirmPassword = 'Passwords do not match';
  }

  return errors;
}

export function RegisterPage() {
  useDocumentTitle('Create account');

  const { register } = useAuth();
  const { showSuccess } = useToast();
  const navigate = useNavigate();

  const [values, setValues] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  function update(field: keyof typeof values, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    // Clears the error as soon as the user starts fixing it, rather than leaving
    // stale red text under a field they have already corrected.
    setFieldErrors((current) => {
      if (!(field in current)) {
        return current;
      }
      const next = { ...current };
      delete next[field];
      return next;
    });
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    const clientErrors = validate(values);
    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors);
      return;
    }

    setSubmitting(true);
    try {
      // No role is sent, and the request type has no field for one. Registration
      // always produces a USER; only an administrator can change that afterwards.
      const user = await register({
        name: values.name.trim(),
        email: values.email.trim(),
        password: values.password,
      });

      showSuccess(`Welcome to FinTrack, ${user.name.split(' ')[0]}.`);
      navigate('/dashboard', { replace: true });
    } catch (caught) {
      const error = toAppError(caught);
      setFormError(error.message);
      setFieldErrors(error.fieldMap);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthShell
      title="Create your account"
      subtitle="Start tracking your income and expenses in a couple of minutes."
      footer={
        <>
          Already have an account? <Link to="/login">Log in</Link>
        </>
      }
    >
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        {formError && <ErrorMessage message={formError} />}

        <TextField
          label="Full name"
          name="name"
          autoComplete="name"
          autoFocus
          required
          value={values.name}
          error={fieldErrors.name}
          onChange={(event) => update('name', event.target.value)}
          placeholder="Abhiram P"
        />

        <TextField
          label="Email"
          type="email"
          name="email"
          autoComplete="email"
          inputMode="email"
          required
          value={values.email}
          error={fieldErrors.email}
          onChange={(event) => update('email', event.target.value)}
          placeholder="you@example.com"
        />

        <TextField
          label="Password"
          type="password"
          name="password"
          autoComplete="new-password"
          required
          value={values.password}
          error={fieldErrors.password}
          hint="At least 8 characters, including a letter and a digit."
          onChange={(event) => update('password', event.target.value)}
        />

        <TextField
          label="Confirm password"
          type="password"
          name="confirmPassword"
          autoComplete="new-password"
          required
          value={values.confirmPassword}
          error={fieldErrors.confirmPassword}
          onChange={(event) => update('confirmPassword', event.target.value)}
        />

        <Button type="submit" size="lg" fullWidth loading={submitting}>
          Create account
        </Button>
      </form>
    </AuthShell>
  );
}
