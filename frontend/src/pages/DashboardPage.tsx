import { useCallback } from 'react';
import { Link } from 'react-router-dom';
import styles from './DashboardPage.module.css';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { EmptyState, ErrorMessage, Skeleton } from '@/components/ui/States';
import { MonthlyTrendChart } from '@/components/charts/MonthlyTrendChart';
import { IncomeExpenseDonut } from '@/components/charts/IncomeExpenseDonut';
import { CategoryBreakdownChart } from '@/components/charts/CategoryBreakdownChart';
import { dashboardService } from '@/services/dashboardService';
import { useApiData } from '@/hooks/useApiData';
import { useAuth } from '@/hooks/useAuth';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { formatCurrency, formatDate, formatSignedCurrency } from '@/utils/format';
import {
  IconInbox,
  IconPlus,
  IconReceipt,
  IconTrendDown,
  IconTrendUp,
  IconWallet,
} from '@/components/ui/Icons';

/** Skeleton shaped like the real cards, so nothing shifts when data lands. */
function StatsSkeleton() {
  return (
    <div className={styles.stats}>
      {[0, 1, 2, 3].map((index) => (
        <div key={index} className={styles.stat}>
          <div className={styles.statTop}>
            <Skeleton width={90} height={14} />
            <Skeleton width={34} height={34} radius="var(--radius-md)" />
          </div>
          <Skeleton width={140} height={30} />
          <Skeleton width={110} height={12} />
        </div>
      ))}
    </div>
  );
}

export function DashboardPage() {
  useDocumentTitle('Dashboard');

  const { user } = useAuth();

  // Wrapped in useCallback so its identity is stable; without it useApiData would
  // see a new function every render and refetch in an endless loop.
  const load = useCallback(() => dashboardService.get(), []);
  const { data, loading, error, reload } = useApiData(load);

  const firstName = user?.name.split(' ')[0] ?? 'there';

  // Only the very first load shows skeletons. A reload keeps the previous figures
  // on screen rather than blanking a dashboard the user is already reading.
  if (loading && !data) {
    return (
      <div className={styles.page}>
        <div className={styles.pageHead}>
          <div className={styles.greeting}>
            <h2 className={styles.greetingTitle}>Welcome back, {firstName}</h2>
            <Skeleton width={260} height={14} />
          </div>
        </div>
        <StatsSkeleton />
        <Card>
          <CardBody>
            <Skeleton height={240} />
          </CardBody>
        </Card>
      </div>
    );
  }

  if (error && !data) {
    return (
      <div className={styles.page}>
        <ErrorMessage
          message={error.message}
          action={
            <Button size="sm" variant="secondary" onClick={reload}>
              Try again
            </Button>
          }
        />
      </div>
    );
  }

  if (!data) {
    return null;
  }

  const hasActivity = data.transactionCount > 0;
  const hasExpenseBreakdown = data.categoryBreakdown.some((row) => row.type === 'EXPENSE');
  const hasIncomeBreakdown = data.categoryBreakdown.some((row) => row.type === 'INCOME');

  return (
    <div className={styles.page}>
      <div className={styles.pageHead}>
        <div className={styles.greeting}>
          <h2 className={styles.greetingTitle}>Welcome back, {firstName}</h2>
          <p className={styles.greetingCopy}>
            {hasActivity
              ? `Your ledger holds ${data.transactionCount} ${data.transactionCount === 1 ? 'transaction' : 'transactions'}.`
              : 'Add your first transaction to see your overview here.'}
          </p>
        </div>

        <Link to="/transactions/new">
          <Button>
            <IconPlus size={16} />
            Add transaction
          </Button>
        </Link>
      </div>

      {/* A stale-data warning shown alongside the figures rather than replacing
          them, so a transient failure does not wipe the screen. */}
      {error && (
        <ErrorMessage
          message={`${error.message} Showing the last figures loaded.`}
          action={
            <Button size="sm" variant="secondary" onClick={reload}>
              Refresh
            </Button>
          }
        />
      )}

      <div className={styles.stats}>
        <div className={`${styles.stat} ${styles.statIncome}`}>
          <div className={styles.statTop}>
            <span className={styles.statLabel}>Total income</span>
            <span className={`${styles.statIcon} ${styles.statIconIncome}`} aria-hidden="true">
              <IconTrendUp size={18} />
            </span>
          </div>
          <span className={`${styles.statValue} ${styles.statPositive}`}>
            {formatCurrency(data.totalIncome)}
          </span>
          <span className={styles.statHint}>All recorded income</span>
        </div>

        <div className={`${styles.stat} ${styles.statExpense}`}>
          <div className={styles.statTop}>
            <span className={styles.statLabel}>Total expenses</span>
            <span className={`${styles.statIcon} ${styles.statIconExpense}`} aria-hidden="true">
              <IconTrendDown size={18} />
            </span>
          </div>
          <span className={`${styles.statValue} ${styles.statNegative}`}>
            {formatCurrency(data.totalExpenses)}
          </span>
          <span className={styles.statHint}>All recorded spending</span>
        </div>

        <div className={`${styles.stat} ${styles.statBalance}`}>
          <div className={styles.statTop}>
            <span className={styles.statLabel}>Current balance</span>
            <span className={`${styles.statIcon} ${styles.statIconBalance}`} aria-hidden="true">
              <IconWallet size={18} />
            </span>
          </div>
          {/* Computed by the backend as income minus expenses. Recomputing it here
              would risk showing a different number than the API reports. */}
          <span
            className={`${styles.statValue} ${data.balance < 0 ? styles.statNegative : ''}`}
          >
            {formatCurrency(data.balance)}
          </span>
          <span className={styles.statHint}>
            {data.balance < 0 ? 'Spending exceeds income' : 'Income minus expenses'}
          </span>
        </div>

        <div className={`${styles.stat} ${styles.statCount}`}>
          <div className={styles.statTop}>
            <span className={styles.statLabel}>Transactions</span>
            <span className={`${styles.statIcon} ${styles.statIconCount}`} aria-hidden="true">
              <IconReceipt size={18} />
            </span>
          </div>
          <span className={styles.statValue}>{data.transactionCount}</span>
          <span className={styles.statHint}>Records in your ledger</span>
        </div>
      </div>

      {!hasActivity ? (
        <Card>
          <EmptyState
            icon={<IconInbox />}
            title="No transactions yet"
            description="Once you record some income or expenses, your balance, category breakdown and monthly trend will appear here."
            action={
              <Link to="/transactions/new">
                <Button>
                  <IconPlus size={16} />
                  Add your first transaction
                </Button>
              </Link>
            }
          />
        </Card>
      ) : (
        <div className={styles.grid}>
          {/* The monthly chart needs at least two months to show a trend; with one
              it would be a single pair of bars saying nothing a stat card does not. */}
          {data.monthlySummary.length >= 2 && (
            <Card className={styles.gridFull}>
              <CardHeader
                title="Income vs expenses"
                subtitle="Month by month, from your recorded transactions"
              />
              <CardBody>
                <MonthlyTrendChart data={data.monthlySummary} />
              </CardBody>
            </Card>
          )}

          <Card>
            <CardHeader title="Recent activity" subtitle="Your five most recent entries" />
            <CardBody flush>
              <div className={styles.recentList}>
                {data.recentTransactions.map((transaction) => (
                  <Link
                    key={transaction.id}
                    to={`/transactions/${transaction.id}`}
                    className={styles.recentRow}
                  >
                    <span
                      className={`${styles.recentIcon} ${
                        transaction.type === 'INCOME'
                          ? styles.recentIconIncome
                          : styles.recentIconExpense
                      }`}
                      aria-hidden="true"
                    >
                      {transaction.type === 'INCOME' ? (
                        <IconTrendUp size={16} />
                      ) : (
                        <IconTrendDown size={16} />
                      )}
                    </span>

                    <span className={styles.recentBody}>
                      <span className={styles.recentTitle}>
                        {/* description is omitted entirely by the API when null,
                            so the category name is the fallback label. */}
                        {transaction.description || transaction.category.name}
                      </span>
                      <span className={styles.recentMeta}>
                        <span>{transaction.category.name}</span>
                        <span aria-hidden="true">·</span>
                        <span>{formatDate(transaction.transactionDate)}</span>
                      </span>
                    </span>

                    <span
                      className={`${styles.recentAmount} ${
                        transaction.type === 'INCOME' ? styles.statPositive : styles.statNegative
                      }`}
                    >
                      {formatSignedCurrency(transaction.amount, transaction.type)}
                    </span>
                  </Link>
                ))}
              </div>
            </CardBody>
            <CardBody>
              <Link to="/transactions" className={styles.viewAll}>
                View all transactions →
              </Link>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Income and expenses" subtitle="Share of total activity" />
            <CardBody>
              <IncomeExpenseDonut
                income={data.totalIncome}
                expenses={data.totalExpenses}
                balance={data.balance}
              />
            </CardBody>
          </Card>

          {hasExpenseBreakdown && (
            <Card>
              <CardHeader title="Top spending categories" subtitle="Share of total expenses" />
              <CardBody>
                <CategoryBreakdownChart data={data.categoryBreakdown} type="EXPENSE" />
              </CardBody>
            </Card>
          )}

          {hasIncomeBreakdown && (
            <Card>
              <CardHeader title="Income sources" subtitle="Share of total income" />
              <CardBody>
                <CategoryBreakdownChart data={data.categoryBreakdown} type="INCOME" />
              </CardBody>
            </Card>
          )}
        </div>
      )}
    </div>
  );
}
