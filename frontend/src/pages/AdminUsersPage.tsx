import { useCallback, useState } from 'react';
import styles from './Pages.module.css';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { SelectField } from '@/components/ui/Field';
import { RoleBadge, StatusBadge } from '@/components/ui/Badge';
import { EmptyState, ErrorMessage } from '@/components/ui/States';
import { LoadingState } from '@/components/ui/Spinner';
import { Pagination } from '@/components/ui/Pagination';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { IconUsers } from '@/components/ui/Icons';
import { userService } from '@/services/userService';
import { useApiData } from '@/hooks/useApiData';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/useToast';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { toAppError } from '@/services/apiError';
import { formatDateTime, initialsOf } from '@/utils/format';
import { ROLES, USER_STATUSES, type Role, type UserFilters, type UserResponse, type UserStatus } from '@/types/api';

const PAGE_SIZE = 20;

/**
 * Account administration.
 *
 * Reached only through `AdminRoute`, and the navigation entry is hidden for other
 * roles — but neither of those is what protects it. `GET /api/users` and the
 * id-addressed routes are restricted to ADMIN in the backend's `SecurityConfig`
 * and again by `@PreAuthorize`, so a user who types the URL reaches a page whose
 * every request returns 403.
 */
export function AdminUsersPage() {
  useDocumentTitle('User management');

  const { user: currentUser } = useAuth();
  const { showSuccess, showError } = useToast();

  const [filters, setFilters] = useState<UserFilters>({});
  const [page, setPage] = useState(0);
  const [pendingDeactivate, setPendingDeactivate] = useState<UserResponse | null>(null);
  const [working, setWorking] = useState(false);

  const load = useCallback(
    () =>
      userService.list(filters, {
        page,
        size: PAGE_SIZE,
        sort: { field: 'createdAt', direction: 'desc' },
      }),
    [filters, page],
  );
  const { data, loading, error, reload } = useApiData(load);

  function updateFilter(key: keyof UserFilters, raw: string) {
    const next = { ...filters };
    if (raw === '') {
      delete next[key];
    } else {
      next[key] = raw as never;
    }
    setFilters(next);
    setPage(0);
  }

  async function changeRole(target: UserResponse, role: Role) {
    setWorking(true);
    try {
      await userService.update(target.id, { role });
      showSuccess(`${target.name} is now ${role.toLowerCase()}.`);
      reload();
    } catch (caught) {
      // The backend refuses to demote the last active administrator and explains
      // why, which is more useful than anything this page could invent.
      showError(toAppError(caught).message);
    } finally {
      setWorking(false);
    }
  }

  async function changeStatus(target: UserResponse, status: UserStatus) {
    setWorking(true);
    try {
      await userService.update(target.id, { status });
      showSuccess(`${target.name} is now ${status.toLowerCase()}.`);
      reload();
    } catch (caught) {
      showError(toAppError(caught).message);
    } finally {
      setWorking(false);
    }
  }

  async function handleDeactivate() {
    if (!pendingDeactivate) {
      return;
    }

    setWorking(true);
    try {
      await userService.deactivate(pendingDeactivate.id);
      showSuccess(`${pendingDeactivate.name} has been deactivated.`);
      setPendingDeactivate(null);
      reload();
    } catch (caught) {
      showError(toAppError(caught).message);
      setPendingDeactivate(null);
    } finally {
      setWorking(false);
    }
  }

  const users = data?.content ?? [];

  return (
    <div className={styles.page}>
      <div className={styles.head}>
        <div className={styles.headText}>
          <p className={styles.lead}>
            Manage roles and account status. Deactivation is a soft delete: the account
            can no longer sign in, but its transaction history is retained.
          </p>
        </div>
      </div>

      <Card>
        <CardHeader
          title="Accounts"
          subtitle={data ? `${data.totalElements} ${data.totalElements === 1 ? 'account' : 'accounts'}` : undefined}
        />

        <div className={styles.filterRow}>
          <div className={styles.filterField}>
            <SelectField
              label="Role"
              value={filters.role ?? ''}
              onChange={(event) => updateFilter('role', event.target.value)}
            >
              <option value="">All roles</option>
              {ROLES.map((role) => (
                <option key={role} value={role}>
                  {role.charAt(0) + role.slice(1).toLowerCase()}
                </option>
              ))}
            </SelectField>
          </div>

          <div className={styles.filterField}>
            <SelectField
              label="Status"
              value={filters.status ?? ''}
              onChange={(event) => updateFilter('status', event.target.value)}
            >
              <option value="">All statuses</option>
              {USER_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {status.charAt(0) + status.slice(1).toLowerCase()}
                </option>
              ))}
            </SelectField>
          </div>
        </div>

        {error && (
          <CardBody>
            <ErrorMessage
              message={error.message}
              action={
                <Button size="sm" variant="secondary" onClick={reload}>
                  Try again
                </Button>
              }
            />
          </CardBody>
        )}

        {loading && !data ? (
          <LoadingState label="Loading accounts…" />
        ) : users.length === 0 ? (
          <EmptyState
            icon={<IconUsers />}
            title="No accounts match these filters"
            description="Try clearing the role or status filter."
          />
        ) : (
          <>
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <caption className="sr-only">Registered accounts</caption>
                <thead>
                  <tr>
                    <th scope="col">User</th>
                    <th scope="col">Role</th>
                    <th scope="col">Status</th>
                    <th scope="col">Created</th>
                    <th scope="col" style={{ textAlign: 'right' }}>
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((account) => {
                    const isSelf = account.id === currentUser?.id;

                    return (
                      <tr key={account.id}>
                        <td>
                          <div className={styles.userCell}>
                            <span className={styles.avatar} aria-hidden="true">
                              {initialsOf(account.name)}
                            </span>
                            <span className={styles.userText}>
                              <span className={styles.userName}>
                                {account.name}
                                {isSelf && <span className={styles.selfTag}> (you)</span>}
                              </span>
                              <span className={styles.userEmail}>{account.email}</span>
                            </span>
                          </div>
                        </td>

                        <td>
                          {/* Editing your own role is disabled: an administrator
                              demoting themselves would immediately lose access to
                              this page, and the backend guards the last admin anyway. */}
                          {isSelf ? (
                            <RoleBadge role={account.role} />
                          ) : (
                            <select
                              value={account.role}
                              disabled={working}
                              aria-label={`Role for ${account.name}`}
                              onChange={(event) => changeRole(account, event.target.value as Role)}
                              style={{
                                padding: 'var(--space-1) var(--space-2)',
                                borderRadius: 'var(--radius-sm)',
                                border: '1px solid var(--border-default)',
                                background: 'var(--bg-surface)',
                                fontSize: 'var(--text-sm)',
                              }}
                            >
                              {ROLES.map((role) => (
                                <option key={role} value={role}>
                                  {role.charAt(0) + role.slice(1).toLowerCase()}
                                </option>
                              ))}
                            </select>
                          )}
                        </td>

                        <td>
                          {isSelf ? (
                            <StatusBadge status={account.status} />
                          ) : (
                            <select
                              value={account.status}
                              disabled={working}
                              aria-label={`Status for ${account.name}`}
                              onChange={(event) =>
                                changeStatus(account, event.target.value as UserStatus)
                              }
                              style={{
                                padding: 'var(--space-1) var(--space-2)',
                                borderRadius: 'var(--radius-sm)',
                                border: '1px solid var(--border-default)',
                                background: 'var(--bg-surface)',
                                fontSize: 'var(--text-sm)',
                              }}
                            >
                              {USER_STATUSES.map((status) => (
                                <option key={status} value={status}>
                                  {status.charAt(0) + status.slice(1).toLowerCase()}
                                </option>
                              ))}
                            </select>
                          )}
                        </td>

                        <td style={{ color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                          {formatDateTime(account.createdAt)}
                        </td>

                        <td>
                          <div className={styles.rowActions}>
                            {!isSelf && account.status !== 'INACTIVE' && (
                              <Button
                                size="sm"
                                variant="dangerGhost"
                                onClick={() => setPendingDeactivate(account)}
                                disabled={working}
                              >
                                Deactivate
                              </Button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {data && (
              <Pagination
                page={data.page}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                pageSize={data.size}
                onPageChange={setPage}
                itemLabel="accounts"
              />
            )}
          </>
        )}
      </Card>

      <ConfirmDialog
        open={pendingDeactivate !== null}
        title="Deactivate this account?"
        message="The account will be marked inactive and can no longer sign in. Its transaction history is retained, and the account can be reactivated later."
        detail={
          pendingDeactivate && (
            <>
              <strong>{pendingDeactivate.name}</strong>
              <br />
              {pendingDeactivate.email}
            </>
          )
        }
        confirmLabel="Deactivate account"
        destructive
        loading={working}
        onConfirm={handleDeactivate}
        onCancel={() => setPendingDeactivate(null)}
      />
    </div>
  );
}
