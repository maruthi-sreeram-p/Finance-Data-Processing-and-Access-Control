import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TransactionFormPage } from '@/pages/TransactionFormPage';
import { ToastProvider } from '@/context/ToastContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { categoryService } from '@/services/categoryService';
import { transactionService } from '@/services/transactionService';
import { AppError } from '@/services/apiError';
import type { CategoryResponse } from '@/types/api';

vi.mock('@/services/categoryService');
vi.mock('@/services/transactionService');

const EXPENSE_CATEGORIES: CategoryResponse[] = [
  { id: 3, name: 'Rent', description: 'Housing', type: 'EXPENSE' },
  { id: 1, name: 'Food', description: 'Groceries', type: 'EXPENSE' },
  { id: 11, name: 'Investment', description: 'Either direction', type: 'BOTH' },
];

function renderForm() {
  return render(
    <ThemeProvider>
      <ToastProvider>
        <MemoryRouter initialEntries={['/transactions/new']}>
          <Routes>
            <Route path="/transactions/new" element={<TransactionFormPage mode="create" />} />
            <Route path="/transactions/:id" element={<h1>Transaction detail</h1>} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </ThemeProvider>,
  );
}

/** Fills every required field with a valid value. */
async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/amount/i), '1250.00');
  await user.selectOptions(screen.getByLabelText(/category/i), '3');
  // Cleared first: the field is pre-filled with today's date.
  const date = screen.getByLabelText(/transaction date/i);
  await user.clear(date);
  await user.type(date, '2026-08-01');
}

describe('TransactionFormPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(categoryService.list).mockResolvedValue(EXPENSE_CATEGORIES);
  });

  it('loads only the categories valid for the chosen direction', async () => {
    renderForm();

    // The picker cannot offer a combination the backend would reject, because the
    // type is passed to the API rather than filtered client-side.
    await waitFor(() => expect(categoryService.list).toHaveBeenCalledWith('EXPENSE'));

    expect(await screen.findByRole('option', { name: 'Rent' })).toBeInTheDocument();
  });

  it('re-queries the categories when the direction changes', async () => {
    const user = userEvent.setup();
    renderForm();

    await waitFor(() => expect(categoryService.list).toHaveBeenCalledWith('EXPENSE'));

    await user.selectOptions(screen.getByLabelText(/type/i), 'INCOME');

    await waitFor(() => expect(categoryService.list).toHaveBeenCalledWith('INCOME'));
  });

  it('rejects an amount below the smallest storable value', async () => {
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('option', { name: 'Rent' });
    await user.type(screen.getByLabelText(/amount/i), '0.001');
    await user.selectOptions(screen.getByLabelText(/category/i), '3');
    await user.click(screen.getByRole('button', { name: /add transaction/i }));

    // 0.001 is positive but rounds to zero at the column scale, which would store
    // a transaction worth nothing -- hence a floor of 0.01, matching the backend.
    expect(await screen.findByText(/at least 0\.01/i)).toBeInTheDocument();
    expect(transactionService.create).not.toHaveBeenCalled();
  });

  it('rejects a future transaction date', async () => {
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('option', { name: 'Rent' });
    await user.type(screen.getByLabelText(/amount/i), '100');
    await user.selectOptions(screen.getByLabelText(/category/i), '3');

    const date = screen.getByLabelText(/transaction date/i);
    await user.clear(date);
    await user.type(date, '2099-01-01');
    await user.click(screen.getByRole('button', { name: /add transaction/i }));

    // A ledger records money that has already moved; a future entry would distort
    // the current balance.
    expect(await screen.findByText(/cannot be in the future/i)).toBeInTheDocument();
    expect(transactionService.create).not.toHaveBeenCalled();
  });

  it('requires a category', async () => {
    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('option', { name: 'Rent' });
    await user.type(screen.getByLabelText(/amount/i), '100');
    await user.click(screen.getByRole('button', { name: /add transaction/i }));

    expect(await screen.findByText(/select a category before saving/i)).toBeInTheDocument();
    expect(transactionService.create).not.toHaveBeenCalled();
  });

  it('submits a valid entry without an owner field', async () => {
    vi.mocked(transactionService.create).mockResolvedValue({
      id: 500,
      userId: 1,
      type: 'EXPENSE',
      amount: 1250,
      category: EXPENSE_CATEGORIES[0]!,
      description: 'August rent',
      transactionDate: '2026-08-01',
      createdAt: '2026-08-01T10:00:00Z',
      updatedAt: '2026-08-01T10:00:00Z',
    });

    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('option', { name: 'Rent' });
    await fillValidForm(user);
    await user.type(screen.getByLabelText(/description/i), 'August rent');
    await user.click(screen.getByRole('button', { name: /add transaction/i }));

    await waitFor(() => {
      expect(transactionService.create).toHaveBeenCalledWith({
        type: 'EXPENSE',
        amount: 1250,
        categoryId: 3,
        transactionDate: '2026-08-01',
        description: 'August rent',
      });
    });

    // No userId is sent, and the request type has no field for one: the owner
    // always comes from the authenticated principal on the server.
    const payload = vi.mocked(transactionService.create).mock.calls[0]?.[0];
    expect(payload).not.toHaveProperty('userId');
  });

  it('omits the description entirely when it is left blank', async () => {
    vi.mocked(transactionService.create).mockResolvedValue({
      id: 501,
      userId: 1,
      type: 'EXPENSE',
      amount: 1250,
      category: EXPENSE_CATEGORIES[0]!,
      transactionDate: '2026-08-01',
      createdAt: '2026-08-01T10:00:00Z',
      updatedAt: '2026-08-01T10:00:00Z',
    });

    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('option', { name: 'Rent' });
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: /add transaction/i }));

    await waitFor(() => expect(transactionService.create).toHaveBeenCalled());

    const payload = vi.mocked(transactionService.create).mock.calls[0]?.[0];
    expect(payload).not.toHaveProperty('description');
  });

  it('marks the field the backend rejected', async () => {
    vi.mocked(transactionService.create).mockRejectedValue(
      new AppError('Request validation failed', 400, 'VALIDATION_FAILED', [
        { field: 'amount', message: 'Amount must be at least 0.01' },
      ]),
    );

    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('option', { name: 'Rent' });
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: /add transaction/i }));

    // The server is the authority even when the client thought the input was fine,
    // so its per-field errors are surfaced on the matching inputs.
    expect(await screen.findByText('Amount must be at least 0.01')).toBeInTheDocument();
  });

  it('surfaces a domain rule the client cannot check', async () => {
    // Category compatibility spans the request and the categories table, so only
    // the backend can decide it.
    vi.mocked(transactionService.create).mockRejectedValue(
      new AppError(
        "Category 'Salary' cannot be used for EXPENSE transactions",
        400,
        'INVALID_TRANSACTION',
      ),
    );

    const user = userEvent.setup();
    renderForm();

    await screen.findByRole('option', { name: 'Rent' });
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: /add transaction/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot be used for EXPENSE/);
  });
});
