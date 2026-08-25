import { describe, expect, it } from 'vitest';
import {
  formatCurrency,
  formatMonth,
  formatSignedCurrency,
  initialsOf,
  parseDateOnly,
  todayAsDateOnly,
} from '@/utils/format';

describe('parseDateOnly', () => {
  it('keeps a calendar date on the day it was recorded', () => {
    // The bug this guards against: `new Date('2026-08-01')` is parsed as UTC
    // midnight, so anywhere west of Greenwich it renders as 31 July. A transaction
    // dated the 1st showing as the 31st is the kind of defect that passes review
    // because it only appears in some timezones.
    const parsed = parseDateOnly('2026-08-01');

    expect(parsed).not.toBeNull();
    expect(parsed?.getFullYear()).toBe(2026);
    expect(parsed?.getMonth()).toBe(7); // zero-based: August
    expect(parsed?.getDate()).toBe(1);
  });

  it('rejects a date that does not exist', () => {
    // The Date constructor silently rolls 31 February forward into March, which
    // would turn a typo into a plausible-looking wrong date.
    expect(parseDateOnly('2026-02-31')).toBeNull();
  });

  it('rejects anything that is not a plain calendar date', () => {
    expect(parseDateOnly('2026-08-01T00:00:00Z')).toBeNull();
    expect(parseDateOnly('01/08/2026')).toBeNull();
    expect(parseDateOnly('')).toBeNull();
  });

  it('round-trips the value produced by todayAsDateOnly', () => {
    expect(parseDateOnly(todayAsDateOnly())).not.toBeNull();
  });
});

describe('formatSignedCurrency', () => {
  it('marks an expense negative and income positive', () => {
    // Amounts are stored positive with the direction held separately, so the sign
    // has to be reintroduced at display time -- a bare figure in a ledger is
    // genuinely ambiguous.
    expect(formatSignedCurrency(1250, 'EXPENSE')).toContain('-');
    expect(formatSignedCurrency(1250, 'INCOME')).toContain('+');
  });
});

describe('formatCurrency', () => {
  it('always shows two decimal places', () => {
    expect(formatCurrency(1250)).toMatch(/1,250\.00/);
    expect(formatCurrency(0)).toMatch(/0\.00/);
  });

  it('formats a negative balance without losing the sign', () => {
    expect(formatCurrency(-999.99)).toContain('-');
  });
});

describe('formatMonth', () => {
  it('renders the API month key as a readable label', () => {
    expect(formatMonth('2026-08')).toBe('Aug 2026');
  });

  it('returns the input unchanged when it is not a month key', () => {
    expect(formatMonth('nonsense')).toBe('nonsense');
  });
});

describe('initialsOf', () => {
  it('uses the first and last name', () => {
    expect(initialsOf('Priya Raman')).toBe('PR');
  });

  it('handles a single name', () => {
    expect(initialsOf('Priya')).toBe('P');
  });

  it('does not crash on an empty name', () => {
    expect(initialsOf('   ')).toBe('?');
  });
});
