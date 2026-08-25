import { Link } from 'react-router-dom';
import styles from './TransactionTable.module.css';
import { CategoryBadge, TypeBadge } from '@/components/ui/Badge';
import { IconEdit, IconEye, IconTrash } from '@/components/ui/Icons';
import { formatDate, formatSignedCurrency } from '@/utils/format';
import type { SortSpec, TransactionResponse, TransactionSortField } from '@/types/api';

interface TransactionTableProps {
  transactions: TransactionResponse[];
  sort: SortSpec;
  onSortChange: (sort: SortSpec) => void;
  onDelete: (transaction: TransactionResponse) => void;
  /**
   * Hides edit and delete for rows the caller does not own.
   *
   * A convenience only. The backend refuses the write regardless -- an
   * administrator viewing another user's ledger receives 404 on PUT and DELETE --
   * so this prevents an action that would visibly fail, not one that would succeed.
   */
  currentUserId?: number;
}

const COLUMNS: Array<{ label: string; field?: TransactionSortField; align?: 'right' }> = [
  { label: 'Date', field: 'transactionDate' },
  { label: 'Description' },
  { label: 'Category' },
  { label: 'Type', field: 'type' },
  { label: 'Amount', field: 'amount', align: 'right' },
  { label: 'Actions', align: 'right' },
];

function SortIcon({ direction }: { direction: 'asc' | 'desc' | null }) {
  return (
    <svg
      className={styles.sortIcon}
      width="12"
      height="12"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {direction === 'asc' ? (
        <path d="m18 15-6-6-6 6" />
      ) : direction === 'desc' ? (
        <path d="m6 9 6 6 6-6" />
      ) : (
        <path d="m7 15 5 5 5-5M7 9l5-5 5 5" />
      )}
    </svg>
  );
}

/**
 * The transaction ledger.
 *
 * Renders as a real `<table>` on wide screens -- this is tabular data, and a grid
 * of divs would lose the row/column relationships that let a screen reader
 * announce "Amount: 1,250" instead of a bare number. Below 820px it becomes a
 * card list instead: six columns cannot be made readable at phone width, and
 * dropping columns to fit would hide information the user came for.
 */
export function TransactionTable({
  transactions,
  sort,
  onSortChange,
  onDelete,
  currentUserId,
}: TransactionTableProps) {
  function toggleSort(field: TransactionSortField) {
    onSortChange(
      sort.field === field
        ? { field, direction: sort.direction === 'asc' ? 'desc' : 'asc' }
        : // A new column starts descending: for dates and amounts, "largest first"
          // is almost always what someone wants to see.
          { field, direction: 'desc' },
    );
  }

  function canModify(transaction: TransactionResponse): boolean {
    return currentUserId === undefined || transaction.userId === currentUserId;
  }

  return (
    <>
      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <caption className="sr-only">
            Your transactions, sorted by {sort.field} {sort.direction === 'asc' ? 'ascending' : 'descending'}
          </caption>
          <thead>
            <tr>
              {COLUMNS.map((column) => {
                const isSorted = column.field !== undefined && sort.field === column.field;

                return (
                  <th
                    key={column.label}
                    scope="col"
                    style={column.align === 'right' ? { textAlign: 'right' } : undefined}
                    // Communicates the current sort to assistive technology, which
                    // cannot see the arrow.
                    aria-sort={
                      isSorted ? (sort.direction === 'asc' ? 'ascending' : 'descending') : undefined
                    }
                  >
                    {column.field ? (
                      <button
                        type="button"
                        className={[styles.sortButton, isSorted ? styles.sortActive : '']
                          .filter(Boolean)
                          .join(' ')}
                        onClick={() => toggleSort(column.field as TransactionSortField)}
                      >
                        {column.label}
                        <SortIcon direction={isSorted ? sort.direction : null} />
                      </button>
                    ) : (
                      column.label
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>

          <tbody>
            {transactions.map((transaction) => (
              <tr key={transaction.id}>
                <td className={styles.date}>{formatDate(transaction.transactionDate)}</td>

                <td>
                  <div className={styles.description}>
                    {transaction.description ?? (
                      <span className={styles.noDescription}>No description</span>
                    )}
                  </div>
                </td>

                <td>
                  <CategoryBadge name={transaction.category.name} type={transaction.category.type} />
                </td>

                <td>
                  <TypeBadge type={transaction.type} />
                </td>

                <td
                  className={`${styles.amount} ${
                    transaction.type === 'INCOME' ? styles.income : styles.expense
                  }`}
                >
                  {formatSignedCurrency(transaction.amount, transaction.type)}
                </td>

                <td>
                  <div className={styles.actions}>
                    <Link
                      to={`/transactions/${transaction.id}`}
                      className={styles.actionButton}
                      aria-label={`View transaction from ${formatDate(transaction.transactionDate)}`}
                    >
                      <IconEye />
                    </Link>

                    {canModify(transaction) && (
                      <>
                        <Link
                          to={`/transactions/${transaction.id}/edit`}
                          className={styles.actionButton}
                          aria-label={`Edit transaction from ${formatDate(transaction.transactionDate)}`}
                        >
                          <IconEdit />
                        </Link>
                        <button
                          type="button"
                          className={`${styles.actionButton} ${styles.actionDanger}`}
                          onClick={() => onDelete(transaction)}
                          aria-label={`Delete transaction from ${formatDate(transaction.transactionDate)}`}
                        >
                          <IconTrash />
                        </button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Card layout for narrow viewports. */}
      <ul className={styles.cards}>
        {transactions.map((transaction) => (
          <li key={transaction.id} className={styles.card}>
            <div className={styles.cardTop}>
              <Link to={`/transactions/${transaction.id}`} className={styles.cardTitle}>
                {transaction.description ?? transaction.category.name}
              </Link>
              <span
                className={`${styles.amount} ${
                  transaction.type === 'INCOME' ? styles.income : styles.expense
                }`}
              >
                {formatSignedCurrency(transaction.amount, transaction.type)}
              </span>
            </div>

            <div className={styles.cardMeta}>
              <CategoryBadge name={transaction.category.name} type={transaction.category.type} />
              <TypeBadge type={transaction.type} />
            </div>

            <div className={styles.cardBottom}>
              <span className={styles.cardMeta}>{formatDate(transaction.transactionDate)}</span>
              <div className={styles.actions}>
                <Link
                  to={`/transactions/${transaction.id}`}
                  className={styles.actionButton}
                  aria-label={`View transaction from ${formatDate(transaction.transactionDate)}`}
                >
                  <IconEye />
                </Link>
                {canModify(transaction) && (
                  <>
                    <Link
                      to={`/transactions/${transaction.id}/edit`}
                      className={styles.actionButton}
                      aria-label={`Edit transaction from ${formatDate(transaction.transactionDate)}`}
                    >
                      <IconEdit />
                    </Link>
                    <button
                      type="button"
                      className={`${styles.actionButton} ${styles.actionDanger}`}
                      onClick={() => onDelete(transaction)}
                      aria-label={`Delete transaction from ${formatDate(transaction.transactionDate)}`}
                    >
                      <IconTrash />
                    </button>
                  </>
                )}
              </div>
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}
