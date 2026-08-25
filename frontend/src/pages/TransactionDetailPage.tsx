import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { ErrorMessage } from '@/components/ui/States';
import { LoadingState } from '@/components/ui/Spinner';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { CategoryBadge, TypeBadge } from '@/components/ui/Badge';
import { IconArrowLeft, IconEdit, IconTrash } from '@/components/ui/Icons';
import { transactionService } from '@/services/transactionService';
import { useApiData } from '@/hooks/useApiData';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/useToast';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { toAppError } from '@/services/apiError';
import { formatCurrency, formatDateTime, formatLongDate, formatSignedCurrency } from '@/utils/format';
import styles from './TransactionDetailPage.module.css';

export function TransactionDetailPage() {
  useDocumentTitle('Transaction');

  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { showSuccess, showError } = useToast();

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const transactionId = id ? Number(id) : null;

  const load = useCallback(
    () =>
      transactionId !== null && Number.isFinite(transactionId)
        ? transactionService.getById(transactionId)
        : Promise.reject(new Error('Invalid transaction id')),
    [transactionId],
  );
  const { data, loading, error } = useApiData(load);

  async function handleDelete() {
    if (transactionId === null) {
      return;
    }

    setDeleting(true);
    try {
      await transactionService.remove(transactionId);
      showSuccess('Transaction deleted.');
      navigate('/transactions', { replace: true });
    } catch (caught) {
      showError(toAppError(caught).message);
      setConfirmOpen(false);
    } finally {
      setDeleting(false);
    }
  }

  if (loading && !data) {
    return <LoadingState label="Loading transaction…" />;
  }

  if (error) {
    return (
      <Card>
        <CardBody>
          <ErrorMessage
            // The backend answers 404 identically for a record that does not exist
            // and one belonging to another user, so this wording covers both
            // without implying the record is real.
            message={
              error.status === 404
                ? 'We could not find that transaction in your ledger.'
                : error.message
            }
            action={
              <Link to="/transactions">
                <Button size="sm" variant="secondary">
                  Back to transactions
                </Button>
              </Link>
            }
          />
        </CardBody>
      </Card>
    );
  }

  if (!data) {
    return null;
  }

  // Read access widens with role, but write access never does: an admin viewing
  // another user's entry gets 404 on PUT and DELETE, so those controls are hidden
  // rather than offered and then rejected.
  const isOwner = user?.id === data.userId;

  return (
    <div className={styles.page}>
      <Link to="/transactions" className={styles.back}>
        <IconArrowLeft />
        Back to transactions
      </Link>

      <Card>
        <CardHeader
          title={data.description ?? data.category.name}
          subtitle={formatLongDate(data.transactionDate)}
          actions={
            isOwner ? (
              <div className={styles.headerActions}>
                <Link to={`/transactions/${data.id}/edit`}>
                  <Button variant="secondary" size="sm">
                    <IconEdit />
                    Edit
                  </Button>
                </Link>
                <Button variant="dangerGhost" size="sm" onClick={() => setConfirmOpen(true)}>
                  <IconTrash />
                  Delete
                </Button>
              </div>
            ) : undefined
          }
        />

        <CardBody>
          <div className={styles.amountBlock}>
            <span className={styles.amountLabel}>Amount</span>
            <span
              className={`${styles.amount} ${
                data.type === 'INCOME' ? styles.income : styles.expense
              }`}
            >
              {formatSignedCurrency(data.amount, data.type)}
            </span>
          </div>

          {/* A description list, not a table: these are label/value pairs about one
              record, and dl is what assistive technology expects for that. */}
          <dl className={styles.details}>
            <div className={styles.detailRow}>
              <dt className={styles.detailLabel}>Type</dt>
              <dd className={styles.detailValue}>
                <TypeBadge type={data.type} />
              </dd>
            </div>

            <div className={styles.detailRow}>
              <dt className={styles.detailLabel}>Category</dt>
              <dd className={styles.detailValue}>
                <CategoryBadge name={data.category.name} type={data.category.type} />
                {data.category.description && (
                  <span className={styles.detailNote}>{data.category.description}</span>
                )}
              </dd>
            </div>

            <div className={styles.detailRow}>
              <dt className={styles.detailLabel}>Description</dt>
              <dd className={styles.detailValue}>
                {data.description ?? <span className={styles.muted}>No description</span>}
              </dd>
            </div>

            <div className={styles.detailRow}>
              <dt className={styles.detailLabel}>Transaction date</dt>
              <dd className={styles.detailValue}>{formatLongDate(data.transactionDate)}</dd>
            </div>

            <div className={styles.detailRow}>
              <dt className={styles.detailLabel}>Transaction ID</dt>
              <dd className={`${styles.detailValue} ${styles.mono}`}>#{data.id}</dd>
            </div>

            <div className={styles.detailRow}>
              <dt className={styles.detailLabel}>Created</dt>
              <dd className={styles.detailValue}>{formatDateTime(data.createdAt)}</dd>
            </div>

            <div className={styles.detailRow}>
              <dt className={styles.detailLabel}>Last updated</dt>
              <dd className={styles.detailValue}>
                {data.updatedAt === data.createdAt ? (
                  <span className={styles.muted}>Never edited</span>
                ) : (
                  formatDateTime(data.updatedAt)
                )}
              </dd>
            </div>

            {/* Shown only when viewing someone else's entry, which only an admin or
                manager can do. It explains why the edit controls are absent. */}
            {!isOwner && (
              <div className={styles.detailRow}>
                <dt className={styles.detailLabel}>Owner</dt>
                <dd className={styles.detailValue}>
                  User #{data.userId}
                  <span className={styles.detailNote}>
                    You can view this record but not change it.
                  </span>
                </dd>
              </div>
            )}
          </dl>
        </CardBody>
      </Card>

      <ConfirmDialog
        open={confirmOpen}
        title="Delete this transaction?"
        message="This permanently removes the record from your ledger. It cannot be undone, and your dashboard totals will change."
        detail={
          <>
            <strong>{data.description ?? data.category.name}</strong>
            <br />
            {formatCurrency(data.amount)} · {data.category.name} ·{' '}
            {formatLongDate(data.transactionDate)}
          </>
        }
        confirmLabel="Delete transaction"
        destructive
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  );
}
