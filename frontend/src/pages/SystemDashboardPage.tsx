import { useCallback, useState } from 'react';
import styles from './Pages.module.css';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { Badge } from '@/components/ui/Badge';
import { EmptyState, ErrorMessage } from '@/components/ui/States';
import { LoadingState } from '@/components/ui/Spinner';
import { MonthlyTrendChart } from '@/components/charts/MonthlyTrendChart';
import { CategoryBreakdownChart } from '@/components/charts/CategoryBreakdownChart';
import { IconInbox, IconShield } from '@/components/ui/Icons';
import { dashboardService } from '@/services/dashboardService';
import { useApiData } from '@/hooks/useApiData';
import { useAuth } from '@/hooks/useAuth';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { formatCurrency, todayAsDateOnly } from '@/utils/format';

/**
 * Organization-wide financial overview, for ADMIN and MANAGER.
 *
 * Reachable only through a role-guarded route, and the nav entry is hidden for
 * other roles — but neither is what protects it. `GET /api/dashboard/system` is
 * restricted by `@PreAuthorize` and by a URL rule in `SecurityConfig`, so a USER
 * who types this URL reaches a page whose only request returns 403.
 *
 * Every figure is a database aggregate across all accounts. There is deliberately
 * no per-user breakdown: this reports what the organization spent, not what each
 * colleague spent.
 */
export function SystemDashboardPage() {
  useDocumentTitle('Organization overview');

  const { user } = useAuth();
  const [range, setRange] = useState<{ from?: string; to?: string }>({});

  const load = useCallback(() => dashboardService.getSystem(range), [range]);
  const { data, loading, error, reload } = useApiData(load);

  function applyRange(key: 'from' | 'to', value: string) {
    setRange((current) => {
      const next = { ...current };
      if (value === '') {
        delete next[key];
      } else {
        next[key] = value;
      }
      return next;
    });
  }

  return (
    <div className={styles.page}>
      <div className={styles.head}>
        <div className={styles.headText}>
          <p className={styles.lead}>
            Totals across every account, aggregated in the database. Visible to
            administrators and managers only.
          </p>
        </div>
        <Badge tone="accent">
          <IconShield size={12} />
          {user?.role === 'ADMIN' ? 'Administrator view' : 'Manager view'}
        </Badge>
      </div>

      <Card>
        <CardHeader title="Period" subtitle="Leave both dates empty to cover all recorded activity" />
        <CardBody>
          <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
            <div style={{ flex: '1 1 200px' }}>
              <TextField
                label="From"
                type="date"
                value={range.from ?? ''}
                max={range.to ?? todayAsDateOnly()}
                onChange={(event) => applyRange('from', event.target.value)}
              />
            </div>
            <div style={{ flex: '1 1 200px' }}>
              <TextField
                label="To"
                type="date"
                value={range.to ?? ''}
                min={range.from ?? undefined}
                max={todayAsDateOnly()}
                onChange={(event) => applyRange('to', event.target.value)}
              />
            </div>
          </div>
        </CardBody>
      </Card>

      {error && (
        <ErrorMessage
          message={
            error.status === 403
              ? 'Your account does not have permission to view organization-wide data.'
              : error.message
          }
          action={
            error.status === 403 ? undefined : (
              <Button size="sm" variant="secondary" onClick={reload}>
                Try again
              </Button>
            )
          }
        />
      )}

      {loading && !data ? (
        <LoadingState label="Aggregating across all accounts…" />
      ) : !data ? null : data.transactionCount === 0 ? (
        <Card>
          <EmptyState
            icon={<IconInbox />}
            title="No activity in this period"
            description="No account recorded a transaction in the selected range."
          />
        </Card>
      ) : (
        <>
          <div className={styles.grid}>
            <div className={styles.categoryCard}>
              <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
                Total income
              </span>
              <strong style={{ fontSize: 'var(--text-2xl)', color: 'var(--positive-text)' }}>
                {formatCurrency(data.totalIncome)}
              </strong>
            </div>
            <div className={styles.categoryCard}>
              <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
                Total expenses
              </span>
              <strong style={{ fontSize: 'var(--text-2xl)', color: 'var(--negative-text)' }}>
                {formatCurrency(data.totalExpenses)}
              </strong>
            </div>
            <div className={styles.categoryCard}>
              <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
                Net position
              </span>
              <strong
                style={{
                  fontSize: 'var(--text-2xl)',
                  color: data.netPosition < 0 ? 'var(--negative-text)' : 'var(--text-primary)',
                }}
              >
                {formatCurrency(data.netPosition)}
              </strong>
            </div>
            <div className={styles.categoryCard}>
              <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
                Activity
              </span>
              <strong style={{ fontSize: 'var(--text-2xl)' }}>{data.transactionCount}</strong>
              <span style={{ fontSize: 'var(--text-xs)', color: 'var(--text-tertiary)' }}>
                across {data.activeLedgerCount}{' '}
                {data.activeLedgerCount === 1 ? 'active account' : 'active accounts'}
              </span>
            </div>
          </div>

          {data.monthlySummary.length >= 2 && (
            <Card>
              <CardHeader
                title="Organization trend"
                subtitle="Income against expenses across all accounts"
              />
              <CardBody>
                <MonthlyTrendChart data={data.monthlySummary} />
              </CardBody>
            </Card>
          )}

          {data.categoryBreakdown.some((row) => row.type === 'EXPENSE') && (
            <Card>
              <CardHeader title="Spending by category" subtitle="All accounts combined" />
              <CardBody>
                <CategoryBreakdownChart data={data.categoryBreakdown} type="EXPENSE" limit={12} />
              </CardBody>
            </Card>
          )}

          {data.categoryBreakdown.some((row) => row.type === 'INCOME') && (
            <Card>
              <CardHeader title="Income by category" subtitle="All accounts combined" />
              <CardBody>
                <CategoryBreakdownChart data={data.categoryBreakdown} type="INCOME" limit={12} />
              </CardBody>
            </Card>
          )}
        </>
      )}
    </div>
  );
}
