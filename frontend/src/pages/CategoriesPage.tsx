import { useCallback, useState, type FormEvent } from 'react';
import styles from './Pages.module.css';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { EmptyState, ErrorMessage } from '@/components/ui/States';
import { LoadingState } from '@/components/ui/Spinner';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { SelectField, TextAreaField, TextField } from '@/components/ui/Field';
import { IconEdit, IconPlus, IconTag, IconTrash } from '@/components/ui/Icons';
import { categoryService } from '@/services/categoryService';
import { useApiData } from '@/hooks/useApiData';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/useToast';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { toAppError } from '@/services/apiError';
import type { CategoryRequest, CategoryResponse, CategoryType } from '@/types/api';

const TYPE_LABELS: Record<CategoryType, string> = {
  INCOME: 'Income only',
  EXPENSE: 'Expenses only',
  BOTH: 'Income or expenses',
};

const EMPTY_FORM: CategoryRequest = { name: '', description: '', type: 'EXPENSE' };

/**
 * Reference categories.
 *
 * Read-only for ordinary users and fully editable for administrators, mirroring
 * the backend exactly: `GET` is open to anyone signed in, while `POST`, `PUT` and
 * `DELETE` are restricted to ADMIN. The management controls are hidden for
 * everyone else — but that is presentation, and a user who forces their way to
 * them still gets a 403 from the API.
 */
export function CategoriesPage() {
  useDocumentTitle('Categories');

  const { hasRole } = useAuth();
  const { showSuccess, showError } = useToast();
  const isAdmin = hasRole('ADMIN');

  const load = useCallback(() => categoryService.list(), []);
  const { data, loading, error, reload } = useApiData(load);

  const [editing, setEditing] = useState<CategoryResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState<CategoryRequest>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<CategoryResponse | null>(null);
  const [deleting, setDeleting] = useState(false);

  const formOpen = creating || editing !== null;

  function startCreate() {
    setEditing(null);
    setCreating(true);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError(null);
  }

  function startEdit(category: CategoryResponse) {
    setCreating(false);
    setEditing(category);
    setForm({
      name: category.name,
      description: category.description ?? '',
      type: category.type,
    });
    setFieldErrors({});
    setFormError(null);
  }

  function closeForm() {
    setCreating(false);
    setEditing(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    const errors: Record<string, string> = {};
    if (!form.name.trim()) {
      errors.name = 'Name is required';
    } else if (form.name.trim().length < 2 || form.name.trim().length > 60) {
      errors.name = 'Name must be between 2 and 60 characters';
    }
    if ((form.description ?? '').length > 255) {
      errors.description = 'Description must not exceed 255 characters';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    const payload: CategoryRequest = {
      name: form.name.trim(),
      type: form.type,
      ...(form.description?.trim() ? { description: form.description.trim() } : {}),
    };

    setSaving(true);
    try {
      if (editing) {
        await categoryService.update(editing.id, payload);
        showSuccess(`Category “${payload.name}” updated.`);
      } else {
        await categoryService.create(payload);
        showSuccess(`Category “${payload.name}” created.`);
      }
      closeForm();
      reload();
    } catch (caught) {
      const appError = toAppError(caught);
      setFormError(appError.message);
      setFieldErrors(appError.fieldMap);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!pendingDelete) {
      return;
    }

    setDeleting(true);
    try {
      await categoryService.remove(pendingDelete.id);
      showSuccess(`Category “${pendingDelete.name}” deleted.`);
      setPendingDelete(null);
      reload();
    } catch (caught) {
      // The backend refuses with 403 while transactions still reference the
      // category, and its message says so — far more useful than a generic one.
      showError(toAppError(caught).message);
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.head}>
        <div className={styles.headText}>
          <p className={styles.lead}>
            Categories classify every transaction. Each one declares which direction it
            accepts, so an expense cannot be filed under an income-only category.
            {isAdmin
              ? ' As an administrator you can add and edit them here.'
              : ' They are shared reference data, managed by an administrator.'}
          </p>
        </div>

        {isAdmin && !formOpen && (
          <Button onClick={startCreate}>
            <IconPlus size={16} />
            New category
          </Button>
        )}
      </div>

      {formOpen && (
        <Card>
          <CardHeader
            title={editing ? `Edit “${editing.name}”` : 'New category'}
            subtitle="Categories are shared by every user, so a change here is visible to everyone."
          />
          <CardBody>
            <form className={styles.form} onSubmit={handleSubmit} noValidate>
              {formError && <ErrorMessage message={formError} />}

              <TextField
                label="Name"
                required
                value={form.name}
                error={fieldErrors.name}
                maxLength={60}
                onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                placeholder="Subscriptions"
              />

              <SelectField
                label="Accepts"
                required
                value={form.type}
                error={fieldErrors.type}
                onChange={(event) =>
                  setForm((current) => ({ ...current, type: event.target.value as CategoryType }))
                }
                hint="Determines which transaction types may use this category."
              >
                <option value="EXPENSE">Expenses only</option>
                <option value="INCOME">Income only</option>
                <option value="BOTH">Income or expenses</option>
              </SelectField>

              <TextAreaField
                label="Description"
                value={form.description ?? ''}
                error={fieldErrors.description}
                maxLength={255}
                onChange={(event) =>
                  setForm((current) => ({ ...current, description: event.target.value }))
                }
                placeholder="Recurring software and media subscriptions"
                hint="Optional."
              />

              <div className={styles.formActions}>
                <Button type="submit" loading={saving}>
                  {editing ? 'Save changes' : 'Create category'}
                </Button>
                <Button type="button" variant="secondary" onClick={closeForm} disabled={saving}>
                  Cancel
                </Button>
              </div>
            </form>
          </CardBody>
        </Card>
      )}

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
        <LoadingState label="Loading categories…" />
      ) : !data || data.length === 0 ? (
        <Card>
          <EmptyState
            icon={<IconTag />}
            title="No categories available"
            description="Categories are seeded with the database. Ask an administrator to add some."
          />
        </Card>
      ) : (
        <div className={styles.grid}>
          {data.map((category) => (
            <div key={category.id} className={styles.categoryCard}>
              <div className={styles.categoryTop}>
                <h3 className={styles.categoryName}>{category.name}</h3>
                <Badge
                  tone={
                    category.type === 'INCOME'
                      ? 'positive'
                      : category.type === 'EXPENSE'
                        ? 'negative'
                        : 'accent'
                  }
                >
                  {TYPE_LABELS[category.type]}
                </Badge>
              </div>

              <p className={styles.categoryDesc}>
                {category.description ?? <span className={styles.muted}>No description</span>}
              </p>

              {isAdmin && (
                <div className={styles.categoryActions}>
                  <Button size="sm" variant="secondary" onClick={() => startEdit(category)}>
                    <IconEdit />
                    Edit
                  </Button>
                  <Button
                    size="sm"
                    variant="dangerGhost"
                    onClick={() => setPendingDelete(category)}
                  >
                    <IconTrash />
                    Delete
                  </Button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete this category?"
        message="Categories are shared by all users. This is refused if any transaction still uses it, so no financial record is ever orphaned."
        detail={pendingDelete && <strong>{pendingDelete.name}</strong>}
        confirmLabel="Delete category"
        destructive
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
