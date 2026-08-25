import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { SelectField, TextAreaField, TextField } from '@/components/ui/Field';
import { ErrorMessage } from '@/components/ui/States';
import { LoadingState } from '@/components/ui/Spinner';
import { IconArrowLeft } from '@/components/ui/Icons';
import { transactionService } from '@/services/transactionService';
import { categoryService } from '@/services/categoryService';
import { useApiData } from '@/hooks/useApiData';
import { useToast } from '@/hooks/useToast';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { toAppError } from '@/services/apiError';
import { todayAsDateOnly } from '@/utils/format';
import type { TransactionRequest, TransactionType } from '@/types/api';

interface TransactionFormPageProps {
  mode: 'create' | 'edit';
}

interface FormValues {
  type: TransactionType;
  amount: string;
  categoryId: string;
  description: string;
  transactionDate: string;
}

const EMPTY_FORM: FormValues = {
  type: 'EXPENSE',
  amount: '',
  categoryId: '',
  description: '',
  transactionDate: todayAsDateOnly(),
};

/**
 * Client-side validation, mirroring the backend constraints.
 *
 * A convenience that saves a round trip, never a control: the server re-validates
 * everything and has the final say. Note the amount rule is `>= 0.01`, matching
 * the backend's `@DecimalMin("0.01")` rather than a plain "positive" check --
 * 0.001 is positive but rounds to zero at the column's scale, which would store a
 * transaction worth nothing.
 */
function validate(values: FormValues): Record<string, string> {
  const errors: Record<string, string> = {};

  if (!values.type) {
    errors.type = 'Choose a transaction type';
  }

  const amount = Number(values.amount);
  if (values.amount.trim() === '') {
    errors.amount = 'Amount is required';
  } else if (!Number.isFinite(amount)) {
    errors.amount = 'Enter a valid number';
  } else if (amount < 0.01) {
    errors.amount = 'Amount must be at least 0.01';
  } else if (!/^\d+(\.\d{1,2})?$/.test(values.amount.trim())) {
    errors.amount = 'Amount can have at most two decimal places';
  }

  if (!values.categoryId) {
    errors.categoryId = 'Select a category before saving';
  }

  if (values.description.length > 255) {
    errors.description = 'Description must not exceed 255 characters';
  }

  if (!values.transactionDate) {
    errors.transactionDate = 'Transaction date is required';
  } else if (values.transactionDate > todayAsDateOnly()) {
    // Matches the backend's @PastOrPresent. A ledger records money that has
    // already moved; a future entry would distort the current balance.
    errors.transactionDate = 'Transaction date cannot be in the future';
  }

  return errors;
}

export function TransactionFormPage({ mode }: TransactionFormPageProps) {
  const isEdit = mode === 'edit';
  useDocumentTitle(isEdit ? 'Edit transaction' : 'Add transaction');

  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { showSuccess } = useToast();

  const [values, setValues] = useState<FormValues>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const transactionId = id ? Number(id) : null;

  // Categories are filtered to those the chosen direction accepts, so the picker
  // cannot offer a combination the backend would reject.
  const loadCategories = useCallback(() => categoryService.list(values.type), [values.type]);
  const { data: categories, loading: categoriesLoading } = useApiData(loadCategories);

  const loadTransaction = useCallback(
    () => (transactionId !== null ? transactionService.getById(transactionId) : Promise.resolve(null)),
    [transactionId],
  );
  const {
    data: existing,
    loading: loadingExisting,
    error: loadError,
  } = useApiData(loadTransaction, isEdit && transactionId !== null);

  // Populates the form once the record arrives.
  useEffect(() => {
    if (!existing) {
      return;
    }
    setValues({
      type: existing.type,
      // toFixed(2) so the input shows "1250.00" rather than "1250", matching how
      // the amount is stored and avoiding a spurious "changed" appearance.
      amount: existing.amount.toFixed(2),
      categoryId: String(existing.category.id),
      description: existing.description ?? '',
      transactionDate: existing.transactionDate,
    });
  }, [existing]);

  /**
   * Clears the category when the direction changes to one it does not accept.
   *
   * Without this, switching from Expense to Income leaves "Rent" selected but no
   * longer present in the list, and the form silently submits a category the
   * backend rejects with a confusing error.
   */
  useEffect(() => {
    if (!categories || !values.categoryId) {
      return;
    }
    const stillValid = categories.some((category) => String(category.id) === values.categoryId);
    if (!stillValid) {
      setValues((current) => ({ ...current, categoryId: '' }));
    }
  }, [categories, values.categoryId]);

  function update<K extends keyof FormValues>(field: K, value: FormValues[K]) {
    setValues((current) => ({ ...current, [field]: value }));
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

    const payload: TransactionRequest = {
      type: values.type,
      amount: Number(values.amount),
      categoryId: Number(values.categoryId),
      transactionDate: values.transactionDate,
      // Omitted rather than sent empty: the backend stores a blank description as
      // null, so sending "" would be normalized away anyway and the intent is
      // clearer this way.
      ...(values.description.trim() ? { description: values.description.trim() } : {}),
    };

    setSubmitting(true);
    try {
      if (isEdit && transactionId !== null) {
        await transactionService.update(transactionId, payload);
        showSuccess('Transaction updated.');
        navigate(`/transactions/${transactionId}`, { replace: true });
      } else {
        const created = await transactionService.create(payload);
        showSuccess('Transaction added.');
        // To the detail page rather than back to the list, so the user sees the
        // record they just created and can act on it immediately.
        navigate(`/transactions/${created.id}`, { replace: true });
      }
    } catch (caught) {
      const error = toAppError(caught);
      setFormError(error.message);
      // The backend reports every rejected field at once, so all of them are
      // marked in a single pass rather than one per submission.
      setFieldErrors(error.fieldMap);
    } finally {
      setSubmitting(false);
    }
  }

  if (isEdit && loadingExisting) {
    return <LoadingState label="Loading transaction…" />;
  }

  // A 404 here means the record does not exist *or* belongs to someone else --
  // the backend deliberately does not distinguish, so neither does this message.
  if (isEdit && loadError) {
    return (
      <Card>
        <CardBody>
          <ErrorMessage
            message={
              loadError.status === 404
                ? 'We could not find that transaction in your ledger.'
                : loadError.message
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

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)', maxWidth: 640 }}>
      <Link
        to={isEdit && transactionId ? `/transactions/${transactionId}` : '/transactions'}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 'var(--space-2)',
          fontSize: 'var(--text-sm)',
          color: 'var(--text-secondary)',
        }}
      >
        <IconArrowLeft />
        {isEdit ? 'Back to transaction' : 'Back to transactions'}
      </Link>

      <Card>
        <CardHeader
          title={isEdit ? 'Edit transaction' : 'Add a transaction'}
          subtitle={
            isEdit
              ? 'Update the details of this ledger entry.'
              : 'Record income or an expense in your ledger.'
          }
        />
        <CardBody>
          <form
            onSubmit={handleSubmit}
            noValidate
            style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}
          >
            {formError && <ErrorMessage message={formError} />}

            <SelectField
              label="Type"
              required
              value={values.type}
              error={fieldErrors.type}
              onChange={(event) => update('type', event.target.value as TransactionType)}
              hint="Determines whether this adds to or subtracts from your balance."
            >
              <option value="EXPENSE">Expense — money out</option>
              <option value="INCOME">Income — money in</option>
            </SelectField>

            <TextField
              label="Amount"
              type="number"
              inputMode="decimal"
              min="0.01"
              step="0.01"
              required
              value={values.amount}
              error={fieldErrors.amount}
              onChange={(event) => update('amount', event.target.value)}
              placeholder="0.00"
              hint="Always a positive figure — the type above sets the direction."
            />

            <SelectField
              label="Category"
              required
              value={values.categoryId}
              error={fieldErrors.categoryId}
              disabled={categoriesLoading}
              onChange={(event) => update('categoryId', event.target.value)}
              hint={
                values.type === 'INCOME'
                  ? 'Showing categories that accept income.'
                  : 'Showing categories that accept expenses.'
              }
            >
              <option value="">
                {categoriesLoading ? 'Loading categories…' : 'Choose a category'}
              </option>
              {(categories ?? []).map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </SelectField>

            <TextField
              label="Transaction date"
              type="date"
              required
              value={values.transactionDate}
              error={fieldErrors.transactionDate}
              // Blocks a future date in the browser's own picker as well as in
              // validation, so the rule is discoverable rather than a rejection.
              max={todayAsDateOnly()}
              onChange={(event) => update('transactionDate', event.target.value)}
              hint="The date the money actually moved."
            />

            <TextAreaField
              label="Description"
              value={values.description}
              error={fieldErrors.description}
              maxLength={255}
              onChange={(event) => update('description', event.target.value)}
              placeholder="August rent, groceries, client invoice…"
              hint={`Optional. ${255 - values.description.length} characters remaining.`}
            />

            <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
              <Button type="submit" loading={submitting}>
                {isEdit ? 'Save changes' : 'Add transaction'}
              </Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => navigate(-1)}
                disabled={submitting}
              >
                Cancel
              </Button>
            </div>
          </form>
        </CardBody>
      </Card>
    </div>
  );
}
