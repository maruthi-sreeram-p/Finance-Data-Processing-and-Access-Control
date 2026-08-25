import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from './Pages.module.css';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { ErrorMessage } from '@/components/ui/States';
import { LoadingState } from '@/components/ui/Spinner';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { RoleBadge, StatusBadge } from '@/components/ui/Badge';
import { userService } from '@/services/userService';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/useToast';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { toAppError } from '@/services/apiError';
import { formatDateTime, initialsOf } from '@/utils/format';

const ROLE_EXPLANATION: Record<string, string> = {
  ADMIN: 'Full access, including account and category administration.',
  MANAGER: 'Can read any user’s ledger and reports, but cannot change them.',
  USER: 'Full control over your own transactions and dashboard.',
};

export function ProfilePage() {
  useDocumentTitle('Profile');

  const { user, refreshUser, logout } = useAuth();
  const { showSuccess } = useToast();
  const navigate = useNavigate();

  const [profile, setProfile] = useState({ name: user?.name ?? '', email: user?.email ?? '' });
  const [profileErrors, setProfileErrors] = useState<Record<string, string>>({});
  const [profileError, setProfileError] = useState<string | null>(null);
  const [savingProfile, setSavingProfile] = useState(false);

  const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [passwordErrors, setPasswordErrors] = useState<Record<string, string>>({});
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [savingPassword, setSavingPassword] = useState(false);

  const [confirmDeactivate, setConfirmDeactivate] = useState(false);
  const [deactivating, setDeactivating] = useState(false);

  if (!user) {
    return <LoadingState label="Loading your profile…" />;
  }

  async function handleProfileSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setProfileError(null);
    setProfileErrors({});

    const errors: Record<string, string> = {};
    if (!profile.name.trim()) {
      errors.name = 'Name is required';
    } else if (profile.name.trim().length < 2 || profile.name.trim().length > 100) {
      errors.name = 'Name must be between 2 and 100 characters';
    }
    if (!profile.email.trim()) {
      errors.email = 'Email is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(profile.email.trim())) {
      errors.email = 'Enter a valid email address';
    }

    if (Object.keys(errors).length > 0) {
      setProfileErrors(errors);
      return;
    }

    setSavingProfile(true);
    try {
      // Only name and email are sent, and the request type has no field for role
      // or status — the backend rejects them here, which is what stops a user
      // promoting themselves through their own profile form.
      await userService.updateCurrent({ name: profile.name.trim(), email: profile.email.trim() });
      await refreshUser();
      showSuccess('Profile updated.');
    } catch (caught) {
      const appError = toAppError(caught);
      setProfileError(appError.message);
      setProfileErrors(appError.fieldMap);
    } finally {
      setSavingProfile(false);
    }
  }

  async function handlePasswordSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPasswordError(null);
    setPasswordErrors({});

    const errors: Record<string, string> = {};
    if (!passwords.currentPassword) {
      errors.currentPassword = 'Enter your current password';
    }
    if (passwords.newPassword.length < 8 || passwords.newPassword.length > 72) {
      errors.newPassword = 'Password must be between 8 and 72 characters';
    } else if (!/[A-Za-z]/.test(passwords.newPassword) || !/[0-9]/.test(passwords.newPassword)) {
      errors.newPassword = 'Password must contain at least one letter and one digit';
    }
    if (passwords.confirm !== passwords.newPassword) {
      errors.confirm = 'Passwords do not match';
    }

    if (Object.keys(errors).length > 0) {
      setPasswordErrors(errors);
      return;
    }

    setSavingPassword(true);
    try {
      await userService.changePassword({
        currentPassword: passwords.currentPassword,
        newPassword: passwords.newPassword,
      });
      // Cleared immediately: there is no reason for a password to sit in
      // component state after the request has completed.
      setPasswords({ currentPassword: '', newPassword: '', confirm: '' });
      showSuccess('Password changed.');
    } catch (caught) {
      const appError = toAppError(caught);
      setPasswordError(appError.message);
      setPasswordErrors(appError.fieldMap);
    } finally {
      setSavingPassword(false);
    }
  }

  async function handleDeactivate() {
    setDeactivating(true);
    try {
      await userService.deactivateCurrent();
      showSuccess('Your account has been deactivated.');
      logout();
      navigate('/login', { replace: true });
    } catch (caught) {
      // The backend refuses to deactivate the last active administrator, and its
      // message explains why.
      setProfileError(toAppError(caught).message);
      setConfirmDeactivate(false);
    } finally {
      setDeactivating(false);
    }
  }

  return (
    <div className={`${styles.page} ${styles.narrow}`}>
      <Card>
        <CardHeader title="Account" subtitle="Details of your FinTrack account" />
        <CardBody>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)', marginBottom: 'var(--space-5)' }}>
            <span className={styles.avatar} style={{ width: 52, height: 52, fontSize: 'var(--text-md)' }}>
              {initialsOf(user.name)}
            </span>
            <div className={styles.userText}>
              <strong style={{ fontSize: 'var(--text-lg)' }}>{user.name}</strong>
              <span style={{ color: 'var(--text-tertiary)', fontSize: 'var(--text-sm)' }}>
                {user.email}
              </span>
            </div>
          </div>

          {/* No password field appears anywhere on this page. The API never returns
              one — UserResponse has no such property — so there is nothing to show
              even accidentally. */}
          <dl className={styles.details}>
            <div className={styles.detailRow}>
              <dt className={styles.detailLabel}>Role</dt>
              <dd className={styles.detailValue}>
                <RoleBadge role={user.role} />
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--text-tertiary)' }}>
                  {ROLE_EXPLANATION[user.role]}
                </span>
              </dd>
            </div>

            <div className={styles.detailRow}>
              <dt className={styles.detailLabel}>Account status</dt>
              <dd className={styles.detailValue}>
                <StatusBadge status={user.status} />
              </dd>
            </div>

            <div className={styles.detailRow}>
              <dt className={styles.detailLabel}>Member since</dt>
              <dd className={styles.detailValue}>{formatDateTime(user.createdAt)}</dd>
            </div>

            <div className={styles.detailRow}>
              <dt className={styles.detailLabel}>Last updated</dt>
              <dd className={styles.detailValue}>{formatDateTime(user.updatedAt)}</dd>
            </div>
          </dl>
        </CardBody>
      </Card>

      <Card>
        <CardHeader
          title="Edit profile"
          subtitle="Your role and account status can only be changed by an administrator."
        />
        <CardBody>
          <form className={styles.form} onSubmit={handleProfileSubmit} noValidate>
            {profileError && <ErrorMessage message={profileError} />}

            <TextField
              label="Full name"
              required
              autoComplete="name"
              value={profile.name}
              error={profileErrors.name}
              onChange={(event) => setProfile((c) => ({ ...c, name: event.target.value }))}
            />

            <TextField
              label="Email"
              type="email"
              required
              autoComplete="email"
              value={profile.email}
              error={profileErrors.email}
              onChange={(event) => setProfile((c) => ({ ...c, email: event.target.value }))}
              hint="Used to log in. Must not already be registered to another account."
            />

            <div className={styles.formActions}>
              <Button type="submit" loading={savingProfile}>
                Save changes
              </Button>
            </div>
          </form>
        </CardBody>
      </Card>

      <Card>
        <CardHeader
          title="Change password"
          subtitle="Your current password is required, so a stolen session alone cannot lock you out."
        />
        <CardBody>
          <form className={styles.form} onSubmit={handlePasswordSubmit} noValidate>
            {passwordError && <ErrorMessage message={passwordError} />}

            <TextField
              label="Current password"
              type="password"
              required
              autoComplete="current-password"
              value={passwords.currentPassword}
              error={passwordErrors.currentPassword}
              onChange={(event) =>
                setPasswords((c) => ({ ...c, currentPassword: event.target.value }))
              }
            />

            <TextField
              label="New password"
              type="password"
              required
              autoComplete="new-password"
              value={passwords.newPassword}
              error={passwordErrors.newPassword}
              hint="At least 8 characters, including a letter and a digit."
              onChange={(event) => setPasswords((c) => ({ ...c, newPassword: event.target.value }))}
            />

            <TextField
              label="Confirm new password"
              type="password"
              required
              autoComplete="new-password"
              value={passwords.confirm}
              error={passwordErrors.confirm}
              onChange={(event) => setPasswords((c) => ({ ...c, confirm: event.target.value }))}
            />

            <div className={styles.formActions}>
              <Button type="submit" loading={savingPassword}>
                Change password
              </Button>
            </div>
          </form>
        </CardBody>
      </Card>

      <div className={styles.dangerZone}>
        <h2 className={styles.dangerTitle}>Deactivate account</h2>
        <p className={styles.dangerCopy}>
          Your account is marked inactive and you will no longer be able to log in. Your
          transaction history is retained rather than deleted, so the records stay
          intact — contact an administrator to restore access.
        </p>
        <div>
          <Button variant="danger" onClick={() => setConfirmDeactivate(true)}>
            Deactivate my account
          </Button>
        </div>
      </div>

      <ConfirmDialog
        open={confirmDeactivate}
        title="Deactivate your account?"
        message="You will be signed out immediately and will not be able to log back in. Your transaction history is kept, and an administrator can reactivate the account."
        confirmLabel="Deactivate account"
        destructive
        loading={deactivating}
        onConfirm={handleDeactivate}
        onCancel={() => setConfirmDeactivate(false)}
      />
    </div>
  );
}
