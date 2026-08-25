import { useState } from 'react';
import styles from './FilterBar.module.css';
import { SelectField, TextField } from '@/components/ui/Field';
import { Button } from '@/components/ui/Button';
import { IconFilter } from '@/components/ui/Icons';
import { todayAsDateOnly } from '@/utils/format';
import type { CategoryResponse, TransactionFilters, TransactionType } from '@/types/api';

interface FilterBarProps {
  filters: TransactionFilters;
  categories: CategoryResponse[];
  onChange: (filters: TransactionFilters) => void;
  onReset: () => void;
}

/**
 * Filter controls for the transaction listing.
 *
 * Every control maps to a query parameter the backend actually accepts, verified
 * against its OpenAPI document. Nothing is filtered client-side: with pagination,
 * filtering the current page would silently exclude matching rows sitting on
 * other pages, which looks like data loss.
 *
 * The date and amount ranges live behind a toggle. Showing eight controls at once
 * makes the common case -- "show me expenses" -- harder to reach than it should be.
 */
export function FilterBar({ filters, categories, onChange, onReset }: FilterBarProps) {
  const [showAdvanced, setShowAdvanced] = useState(
    // Opened automatically when an advanced filter is already applied, so a
    // restored or shared state does not hide the reason the list looks narrow.
    Boolean(filters.from || filters.to || filters.minAmount || filters.maxAmount),
  );

  const activeCount = Object.values(filters).filter(
    (value) => value !== undefined && value !== '' && value !== null,
  ).length;

  /** Empty select or input means "no filter", so the key is removed entirely. */
  function updateOptional<K extends keyof TransactionFilters>(key: K, raw: string) {
    const next = { ...filters };
    if (raw === '') {
      delete next[key];
    } else {
      next[key] = raw as TransactionFilters[K];
    }
    onChange(next);
  }

  function updateNumeric(key: 'minAmount' | 'maxAmount', raw: string) {
    const next = { ...filters };
    if (raw === '') {
      delete next[key];
    } else {
      const parsed = Number(raw);
      if (Number.isFinite(parsed) && parsed >= 0) {
        next[key] = parsed;
      }
    }
    onChange(next);
  }

  return (
    <div className={styles.bar}>
      <div className={styles.row}>
        <div className={styles.field}>
          <SelectField
            label="Type"
            value={filters.type ?? ''}
            onChange={(event) => updateOptional('type', event.target.value as TransactionType | '')}
          >
            <option value="">All types</option>
            <option value="INCOME">Income</option>
            <option value="EXPENSE">Expense</option>
          </SelectField>
        </div>

        <div className={styles.field}>
          <SelectField
            label="Category"
            value={filters.categoryId ?? ''}
            onChange={(event) => {
              const raw = event.target.value;
              const next = { ...filters };
              if (raw === '') {
                delete next.categoryId;
              } else {
                next.categoryId = Number(raw);
              }
              onChange(next);
            }}
          >
            <option value="">All categories</option>
            {/* Options come from the API, never a hardcoded list: category ids are
                not sequential by name, and an administrator can add more. */}
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </SelectField>
        </div>

        <Button
          variant="secondary"
          onClick={() => setShowAdvanced((open) => !open)}
          aria-expanded={showAdvanced}
        >
          <IconFilter />
          {showAdvanced ? 'Fewer filters' : 'More filters'}
        </Button>
      </div>

      {showAdvanced && (
        <div className={styles.advanced}>
          <div className={styles.fieldNarrow}>
            <TextField
              label="From date"
              type="date"
              value={filters.from ?? ''}
              max={filters.to ?? todayAsDateOnly()}
              onChange={(event) => updateOptional('from', event.target.value)}
            />
          </div>

          <div className={styles.fieldNarrow}>
            <TextField
              label="To date"
              type="date"
              value={filters.to ?? ''}
              // Bounded below by `from` so the browser's own picker prevents the
              // inverted range that the backend would reject with a 400.
              min={filters.from ?? undefined}
              max={todayAsDateOnly()}
              onChange={(event) => updateOptional('to', event.target.value)}
            />
          </div>

          <div className={styles.fieldNarrow}>
            <TextField
              label="Min amount"
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              value={filters.minAmount ?? ''}
              onChange={(event) => updateNumeric('minAmount', event.target.value)}
              placeholder="0.00"
            />
          </div>

          <div className={styles.fieldNarrow}>
            <TextField
              label="Max amount"
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              value={filters.maxAmount ?? ''}
              onChange={(event) => updateNumeric('maxAmount', event.target.value)}
              placeholder="No limit"
            />
          </div>
        </div>
      )}

      {activeCount > 0 && (
        <div className={styles.toggleRow}>
          <span className={styles.activeCount}>
            <span className={styles.chip}>
              {activeCount} {activeCount === 1 ? 'filter' : 'filters'} applied
            </span>
          </span>
          <Button variant="ghost" size="sm" onClick={onReset}>
            Clear all
          </Button>
        </div>
      )}
    </div>
  );
}
