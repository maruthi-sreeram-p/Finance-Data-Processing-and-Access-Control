import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { EmptyState, ErrorMessage } from '@/components/ui/States';
import { LoadingState } from '@/components/ui/Spinner';
import { Pagination } from '@/components/ui/Pagination';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { TransactionTable } from '@/components/transactions/TransactionTable';
import { FilterBar } from '@/components/transactions/FilterBar';
import { IconInbox, IconPlus } from '@/components/ui/Icons';
import { transactionService } from '@/services/transactionService';
import { categoryService } from '@/services/categoryService';
import { useApiData } from '@/hooks/useApiData';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/useToast';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { toAppError } from '@/services/apiError';
import { formatCurrency, formatDate } from '@/utils/format';
import type { SortSpec, TransactionFilters, TransactionResponse } from '@/types/api';

const PAGE_SIZE = 20;

const DEFAULT_SORT: SortSpec = { field: 'transactionDate', direction: 'desc' };

export function TransactionsPage() {
  useDocumentTitle('Transactions');

  const { user } = useAuth();
  const { showSuccess, showError } = useToast();

  const [filters, setFilters] = useState<TransactionFilters>({});
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortSpec>(DEFAULT_SORT);
  const [pendingDelete, setPendingDelete] = useState<TransactionResponse | null>(null);
  const [deleting, setDeleting] = useState(false);

  // Amount fields fire on every keystroke; debouncing turns a burst of requests
  // into one. The date and select filters go through the same path for consistency.
  const debouncedFilters = useDebouncedValue(filters, 350);

  const loadCategories = useCallback(() => categoryService.list(), []);
  const { data: categories } = useApiData(loadCategories);

  const loadTransactions = useCallback(
    () => transactionService.list(debouncedFilters, { page, size: PAGE_SIZE, sort }),
    [debouncedFilters, page, sort],
  );
  const { data, loading, error, reload } = useApiData(loadTransactions);

  const hasFilters = useMemo(
    () => Object.values(debouncedFilters).some((value) => value !== undefined && value !== ''),
    [debouncedFilters],
  );

  function handleFilterChange(next: TransactionFilters) {
    setFilters(next);
    // Back to the first page: staying on page 4 of a newly narrowed result set
    // usually lands the user on an empty page that looks like "no results".
    setPage(0);
  }

  function handleSortChange(next: SortSpec) {
    setSort(next);
    setPage(0);
  }

  function handleReset() {
    setFilters({});
    setPage(0);
    setSort(DEFAULT_SORT);
  }

  async function handleConfirmDelete() {
    if (!pendingDelete) {
      return;
    }

    setDeleting(true);
    try {
      await transactionService.remove(pendingDelete.id);
      showSuccess('Transaction deleted.');
      setPendingDelete(null);

      // If the last row on the final page was removed, that page no longer
      // exists. Stepping back avoids showing an empty page with live pagination.
      if (data && data.content.length === 1 && page > 0) {
        setPage((current) => current - 1);
      } else {
        reload();
      }
    } catch (caught) {
      // The backend answers 404 both for a missing record and for someone else's,
      // so the message it returns is already the right thing to show.
      showError(toAppError(caught).message);
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  const transactions = data?.content ?? [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 'var(--space-4)',
          flexWrap: 'wrap',
        }}
      >
        <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
          Every entry in your ledger, filtered and sorted however you need.
        </p>
        <Link to="/transactions/new">
          <Button>
            <IconPlus size={16} />
            Add transaction
          </Button>
        </Link>
      </div>

      <Card>
        <CardHeader
          title="Transactions"
          subtitle={
            data
              ? `${data.totalElements} ${data.totalElements === 1 ? 'record' : 'records'}${hasFilters ? ' matching your filters' : ''}`
              : undefined
          }
        />

        <FilterBar
          filters={filters}
          categories={categories ?? []}
          onChange={handleFilterChange}
          onReset={handleReset}
        />

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
          <LoadingState label="Loading transactions…" />
        ) : transactions.length === 0 ? (
          <EmptyState
            icon={<IconInbox />}
            title={hasFilters ? 'No transactions match these filters' : 'No transactions yet'}
            description={
              hasFilters
                ? 'Try widening the date range or clearing a filter.'
                : 'Record your income and expenses to build up your financial picture.'
            }
            action={
              hasFilters ? (
                <Button variant="secondary" onClick={handleReset}>
                  Clear filters
                </Button>
              ) : (
                <Link to="/transactions/new">
                  <Button>
                    <IconPlus size={16} />
                    Add your first transaction
                  </Button>
                </Link>
              )
            }
          />
        ) : (
          <>
            <TransactionTable
              transactions={transactions}
              sort={sort}
              onSortChange={handleSortChange}
              onDelete={setPendingDelete}
              currentUserId={user?.id}
            />
            {data && (
              <Pagination
                page={data.page}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                pageSize={data.size}
                onPageChange={setPage}
                itemLabel="transactions"
              />
            )}
          </>
        )}
      </Card>

      {/* Financial records are never removed without an explicit confirmation that
          names the record, so the user can see exactly what is about to go. */}
      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete this transaction?"
        message="This permanently removes the record from your ledger. It cannot be undone, and your dashboard totals will change."
        detail={
          pendingDelete && (
            <>
              <strong>{pendingDelete.description ?? pendingDelete.category.name}</strong>
              <br />
              {formatCurrency(pendingDelete.amount)} · {pendingDelete.category.name} ·{' '}
              {formatDate(pendingDelete.transactionDate)}
            </>
          )
        }
        confirmLabel="Delete transaction"
        destructive
        loading={deleting}
        onConfirm={handleConfirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
