import styles from './Charts.module.css';
import type { CategoryBreakdown, TransactionType } from '@/types/api';
import { formatCurrency, formatPercent } from '@/utils/format';

interface CategoryBreakdownChartProps {
  data: CategoryBreakdown[];
  /** Which direction to show. The two are never mixed -- see below. */
  type: TransactionType;
  /** Cap the number of rows, so a long tail does not dominate the panel. */
  limit?: number;
}

/**
 * Spending (or earning) by category, as a ranked bar list.
 *
 * Horizontal bars rather than a pie: with eight or more categories, pie slices
 * become impossible to compare and the labels collide. A ranked list reads in the
 * order that matters -- largest first -- and every value is legible as a figure
 * rather than estimated from an angle.
 *
 * Income and expense rows are never combined. A category breakdown mixing the two
 * would put "Salary" and "Rent" on the same scale, which measures nothing. The
 * `percentage` the backend supplies is already relative to its own direction's
 * total, so the rows shown here sum to 100%.
 */
export function CategoryBreakdownChart({ data, type, limit = 8 }: CategoryBreakdownChartProps) {
  // Already ordered largest-first by the database, so this preserves that order.
  const rows = data.filter((entry) => entry.type === type).slice(0, limit);

  if (rows.length === 0) {
    return null;
  }

  // Bars are scaled against the largest row, not against 100%. Scaling to 100%
  // would leave every bar a stub whenever spending is spread across many
  // categories, wasting the width that makes the comparison readable.
  const largest = Math.max(...rows.map((row) => row.total));

  return (
    <div className={styles.catList}>
      {rows.map((row) => (
        <div key={`${row.categoryId}-${row.type}`} className={styles.catRow}>
          <div className={styles.catHead}>
            <span className={styles.catName}>{row.categoryName}</span>
            <span className={styles.catMeta}>
              <span className={styles.catAmount}>{formatCurrency(row.total)}</span>
              <span className={styles.catPercent}>{formatPercent(row.percentage)}</span>
            </span>
          </div>

          <div
            className={styles.catTrack}
            role="meter"
            aria-valuenow={Math.round(row.percentage)}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={`${row.categoryName}: ${formatCurrency(row.total)}, ${formatPercent(row.percentage)} of ${type === 'INCOME' ? 'income' : 'expenses'}, across ${row.transactionCount} ${row.transactionCount === 1 ? 'transaction' : 'transactions'}`}
          >
            <span
              className={`${styles.catFill} ${type === 'INCOME' ? styles.catFillIncome : styles.catFillExpense}`}
              // A 1% floor keeps a very small category visible as a sliver rather
              // than rendering as an empty track that looks like a bug.
              style={{ width: `${Math.max((row.total / largest) * 100, 1)}%` }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}
