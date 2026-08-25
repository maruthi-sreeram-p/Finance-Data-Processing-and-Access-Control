import { useCallback, useMemo, useState } from 'react';
import styles from './Pages.module.css';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { EmptyState, ErrorMessage } from '@/components/ui/States';
import { LoadingState } from '@/components/ui/Spinner';
import { MonthlyTrendChart } from '@/components/charts/MonthlyTrendChart';
import { CategoryBreakdownChart } from '@/components/charts/CategoryBreakdownChart';
import { IconInbox } from '@/components/ui/Icons';
import { dashboardService } from '@/services/dashboardService';
import { useApiData } from '@/hooks/useApiData';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { formatCurrency, formatMonth, todayAsDateOnly } from '@/utils/format';

/** Presets covering the periods people actually ask for. */
function presetRange(preset: 'thisMonth' | 'last3' | 'thisYear' | 'all'): { from?: string; to?: string } {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  const asDate = (date: Date) =>
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

  switch (preset) {
    case 'thisMonth':
      return { from: asDate(new Date(now.getFullYear(), now.getMonth(), 1)), to: todayAsDateOnly() };
    case 'last3':
      return {
        from: asDate(new Date(now.getFullYear(), now.getMonth() - 2, 1)),
        to: todayAsDateOnly(),
      };
    case 'thisYear':
      return { from: `${now.getFullYear()}-01-01`, to: todayAsDateOnly() };
    case 'all':
    default:
      // Both bounds omitted, which the backend treats as unbounded.
      return {};
  }
}

/**
 * Period reports.
 *
 * Built on `GET /api/dashboard?from=&to=` rather than on a new endpoint: the
 * dashboard already returns exactly the aggregates a report needs, scoped to a
 * date range, and adding a second endpoint returning the same figures would be
 * duplicated backend logic with two places to keep correct.
 *
 * Every number here is a database aggregate. Nothing is summed in the browser.
 */
export function ReportsPage() {
  useDocumentTitle('Reports');

  const [range, setRange] = useState<{ from?: string; to?: string }>(() => presetRange('thisYear'));
  const [activePreset, setActivePreset] = useState<string>('thisYear');

  const load = useCallback(() => dashboardService.get(range), [range]);
  const { data, loading, error, reload } = useApiData(load);

  function applyPreset(preset: 'thisMonth' | 'last3' | 'thisYear' | 'all') {
    setRange(presetRange(preset));
    setActivePreset(preset);
  }

  function applyCustom(key: 'from' | 'to', value: string) {
    setActivePreset('custom');
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

  const periodLabel = useMemo(() => {
    if (!data) {
      return '';
    }
    if (!data.periodFrom && !data.periodTo) {
      return 'All time';
    }
    // periodFrom/periodTo are omitted from the payload when unbounded, so both
    // halves have to tolerate being absent.
    return `${data.periodFrom ?? 'the beginning'} to ${data.periodTo ?? 'today'}`;
  }, [data]);

  const savingsRate = useMemo(() => {
    if (!data || data.totalIncome <= 0) {
      return null;
    }
    // The one derived figure on this page. It is a ratio of two authoritative
    // totals, not a re-summed aggregate, so it cannot disagree with them.
    return ((data.balance / data.totalIncome) * 100).toFixed(1);
  }, [data]);

  return (
    <div className={styles.page}>
      <div className={styles.head}>
        <div className={styles.headText}>
          <p className={styles.lead}>
            Analyse any period of your ledger. Totals, category breakdowns and monthly
            trends are all computed from your recorded transactions.
          </p>
        </div>
      </div>

      <Card>
        <CardHeader title="Period" subtitle="Choose a preset or set your own dates" />
        <CardBody>
          <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap', marginBottom: 'var(--space-5)' }}>
            {(
              [
                ['thisMonth', 'This month'],
                ['last3', 'Last 3 months'],
                ['thisYear', 'This year'],
                ['all', 'All time'],
              ] as const
            ).map(([key, label]) => (
              <Button
                key={key}
                size="sm"
                variant={activePreset === key ? 'primary' : 'secondary'}
                onClick={() => applyPreset(key)}
                aria-pressed={activePreset === key}
              >
                {label}
              </Button>
            ))}
          </div>

          <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
            <div style={{ flex: '1 1 180px' }}>
              <TextField
                label="From"
                type="date"
                value={range.from ?? ''}
                max={range.to ?? todayAsDateOnly()}
                onChange={(event) => applyCustom('from', event.target.value)}
              />
            </div>
            <div style={{ flex: '1 1 180px' }}>
              <TextField
                label="To"
                type="date"
                value={range.to ?? ''}
                min={range.from ?? undefined}
                max={todayAsDateOnly()}
                onChange={(event) => applyCustom('to', event.target.value)}
              />
            </div>
          </div>
        </CardBody>
      </Card>

      {error && (
        <ErrorMessage
          message={error.message}
          action={
            <Button size="sm" variant="secondary" onClick={reload}>
              Try again
            </Button>
          }
        />
      )}

      {loading && !data ? (
        <LoadingState label="Building your report…" />
      ) : !data ? null : data.transactionCount === 0 ? (
        <Card>
          <EmptyState
            icon={<IconInbox />}
            title="No transactions in this period"
            description="Try widening the date range, or choose “All time” to see everything recorded so far."
          />
        </Card>
      ) : (
        <>
          <Card>
            <CardHeader title="Summary" subtitle={periodLabel} />
            <CardBody>
              <dl className={styles.details}>
                <div className={styles.detailRow}>
                  <dt className={styles.detailLabel}>Total income</dt>
                  <dd className={styles.detailValue} style={{ color: 'var(--positive-text)', fontWeight: 600 }}>
                    {formatCurrency(data.totalIncome)}
                  </dd>
                </div>
                <div className={styles.detailRow}>
                  <dt className={styles.detailLabel}>Total expenses</dt>
                  <dd className={styles.detailValue} style={{ color: 'var(--negative-text)', fontWeight: 600 }}>
                    {formatCurrency(data.totalExpenses)}
                  </dd>
                </div>
                <div className={styles.detailRow}>
                  <dt className={styles.detailLabel}>Net balance</dt>
                  <dd
                    className={styles.detailValue}
                    style={{
                      color: data.balance < 0 ? 'var(--negative-text)' : 'var(--text-primary)',
                      fontWeight: 600,
                    }}
                  >
                    {formatCurrency(data.balance)}
                  </dd>
                </div>
                <div className={styles.detailRow}>
                  <dt className={styles.detailLabel}>Transactions</dt>
                  <dd className={styles.detailValue}>{data.transactionCount}</dd>
                </div>
                {savingsRate !== null && (
                  <div className={styles.detailRow}>
                    <dt className={styles.detailLabel}>Share of income kept</dt>
                    <dd className={styles.detailValue}>
                      {savingsRate}%
                      <span style={{ fontSize: 'var(--text-xs)', color: 'var(--text-tertiary)' }}>
                        Balance as a proportion of income
                      </span>
                    </dd>
                  </div>
                )}
                {data.monthlySummary.length > 0 && (
                  <div className={styles.detailRow}>
                    <dt className={styles.detailLabel}>Months covered</dt>
                    <dd className={styles.detailValue}>
                      {data.monthlySummary.length}
                      <span style={{ fontSize: 'var(--text-xs)', color: 'var(--text-tertiary)' }}>
                        {formatMonth(data.monthlySummary[0]?.month ?? '')} –{' '}
                        {formatMonth(data.monthlySummary[data.monthlySummary.length - 1]?.month ?? '')}
                      </span>
                    </dd>
                  </div>
                )}
              </dl>
            </CardBody>
          </Card>

          {data.monthlySummary.length >= 2 && (
            <Card>
              <CardHeader title="Monthly trend" subtitle="Income against expenses over the period" />
              <CardBody>
                <MonthlyTrendChart data={data.monthlySummary} />
              </CardBody>
            </Card>
          )}

          {data.categoryBreakdown.some((row) => row.type === 'EXPENSE') && (
            <Card>
              <CardHeader title="Where the money went" subtitle="Expenses by category" />
              <CardBody>
                <CategoryBreakdownChart data={data.categoryBreakdown} type="EXPENSE" limit={12} />
              </CardBody>
            </Card>
          )}

          {data.categoryBreakdown.some((row) => row.type === 'INCOME') && (
            <Card>
              <CardHeader title="Where the money came from" subtitle="Income by category" />
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
