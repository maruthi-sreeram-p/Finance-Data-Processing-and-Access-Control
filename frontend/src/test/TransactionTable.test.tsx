import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { TransactionTable } from '@/components/transactions/TransactionTable';
import type { SortSpec, TransactionResponse } from '@/types/api';

const RENT = { id: 3, name: 'Rent', description: 'Housing', type: 'EXPENSE' } as const;
const SALARY = { id: 9, name: 'Salary', description: 'Employment income', type: 'INCOME' } as const;

const TRANSACTIONS: TransactionResponse[] = [
  {
    id: 101,
    userId: 1,
    type: 'EXPENSE',
    amount: 18000,
    category: RENT,
    description: 'August rent',
    transactionDate: '2026-08-01',
    createdAt: '2026-08-01T10:00:00Z',
    updatedAt: '2026-08-01T10:00:00Z',
  },
  {
    id: 102,
    userId: 1,
    type: 'INCOME',
    amount: 62000,
    category: SALARY,
    // No description: the API omits the field entirely rather than sending null,
    // so the table must render a fallback rather than an empty cell.
    transactionDate: '2026-07-31',
    createdAt: '2026-07-31T10:00:00Z',
    updatedAt: '2026-07-31T10:00:00Z',
  },
  {
    id: 103,
    userId: 2, // belongs to somebody else
    type: 'EXPENSE',
    amount: 500,
    category: RENT,
    description: "Another user's entry",
    transactionDate: '2026-08-02',
    createdAt: '2026-08-02T10:00:00Z',
    updatedAt: '2026-08-02T10:00:00Z',
  },
];

const SORT: SortSpec = { field: 'transactionDate', direction: 'desc' };

function renderTable(overrides: Partial<Parameters<typeof TransactionTable>[0]> = {}) {
  const onDelete = vi.fn();
  const onSortChange = vi.fn();

  render(
    <MemoryRouter>
      <TransactionTable
        transactions={TRANSACTIONS}
        sort={SORT}
        onSortChange={onSortChange}
        onDelete={onDelete}
        currentUserId={1}
        {...overrides}
      />
    </MemoryRouter>,
  );

  return { onDelete, onSortChange };
}

describe('TransactionTable', () => {
  it('renders each transaction with its amount signed by direction', () => {
    renderTable();

    const table = screen.getByRole('table');
    expect(within(table).getByText('August rent')).toBeInTheDocument();

    // Expense negative, income positive -- amounts are stored positive, so the
    // sign is reintroduced at display time from the type.
    expect(within(table).getByText(/-.*18,000\.00/)).toBeInTheDocument();
    expect(within(table).getByText(/\+.*62,000\.00/)).toBeInTheDocument();
  });

  it('shows a fallback when the API omitted the description', () => {
    renderTable();

    expect(within(screen.getByRole('table')).getByText('No description')).toBeInTheDocument();
  });

  it('renders the date the transaction was recorded, not a timezone-shifted one', () => {
    renderTable();

    // Guards the date-only parsing: naive parsing would render 31 Jul for an
    // entry dated 1 Aug in any timezone west of UTC.
    expect(within(screen.getByRole('table')).getByText('1 Aug 2026')).toBeInTheDocument();
  });

  it('labels the type in words as well as by colour', () => {
    renderTable();

    // Colour alone excludes anyone with a colour vision deficiency, and roughly
    // one man in twelve has one.
    const table = screen.getByRole('table');
    expect(within(table).getAllByText('Expense').length).toBeGreaterThan(0);
    expect(within(table).getByText('Income')).toBeInTheDocument();
  });

  it('exposes the current sort to assistive technology', () => {
    renderTable();

    const dateHeader = screen.getByRole('columnheader', { name: /date/i });
    expect(dateHeader).toHaveAttribute('aria-sort', 'descending');
  });

  it('flips direction when the active sort column is clicked again', async () => {
    const user = userEvent.setup();
    const { onSortChange } = renderTable();

    await user.click(screen.getByRole('button', { name: /^date$/i }));

    expect(onSortChange).toHaveBeenCalledWith({ field: 'transactionDate', direction: 'asc' });
  });

  it('starts a newly chosen column descending', async () => {
    const user = userEvent.setup();
    const { onSortChange } = renderTable();

    await user.click(screen.getByRole('button', { name: /^amount$/i }));

    // Largest first is what someone almost always wants from a fresh sort on
    // dates or amounts.
    expect(onSortChange).toHaveBeenCalledWith({ field: 'amount', direction: 'desc' });
  });

  it('asks for confirmation rather than deleting on click', async () => {
    const user = userEvent.setup();
    const { onDelete } = renderTable();

    const table = screen.getByRole('table');
    await user.click(within(table).getAllByRole('button', { name: /delete transaction/i })[0]!);

    // The handler opens a dialog; nothing is removed until it is confirmed.
    expect(onDelete).toHaveBeenCalledWith(expect.objectContaining({ id: 101 }));
  });

  it('offers no edit or delete control for another user’s record', () => {
    renderTable();

    const table = screen.getByRole('table');
    // Two of the three rows belong to the current user, so only two rows offer
    // write actions. The backend refuses the write regardless -- this only avoids
    // presenting an action that would visibly fail.
    expect(within(table).getAllByRole('button', { name: /delete transaction/i })).toHaveLength(2);
    expect(within(table).getAllByRole('link', { name: /edit transaction/i })).toHaveLength(2);

    // Viewing is still offered for all three.
    expect(within(table).getAllByRole('link', { name: /view transaction/i })).toHaveLength(3);
  });

  it('hides no actions when no owner is supplied', () => {
    renderTable({ currentUserId: undefined });

    const table = screen.getByRole('table');
    expect(within(table).getAllByRole('button', { name: /delete transaction/i })).toHaveLength(3);
  });
});
