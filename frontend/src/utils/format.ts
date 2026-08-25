import type { TransactionType } from '@/types/api';

/**
 * Display formatting for money, dates and labels.
 *
 * Centralized so that a figure looks the same everywhere it appears, and so the
 * two genuinely subtle problems below are solved once rather than rediscovered.
 */

/**
 * The display currency.
 *
 * FinTrack stores amounts without a currency: the backend models a single-currency
 * ledger, so this is a presentation choice rather than data. It lives here as one
 * constant so multi-currency support later means changing this file, not hunting
 * through components.
 */
const CURRENCY = 'INR';
const LOCALE = 'en-IN';

const currencyFormatter = new Intl.NumberFormat(LOCALE, {
  style: 'currency',
  currency: CURRENCY,
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const compactFormatter = new Intl.NumberFormat(LOCALE, {
  style: 'currency',
  currency: CURRENCY,
  notation: 'compact',
  maximumFractionDigits: 1,
});

/**
 * Formats a monetary amount.
 *
 * The client formats money but never computes it: JavaScript numbers are IEEE
 * doubles and cannot represent 0.10 exactly, so summing a page of amounts here
 * would drift away from the authoritative total. Every total, balance and
 * percentage shown in FinTrack comes from a database aggregate.
 */
export function formatCurrency(amount: number): string {
  return currencyFormatter.format(amount);
}

/** Abbreviated form for chart axes, where a full figure would not fit. */
export function formatCompactCurrency(amount: number): string {
  return compactFormatter.format(amount);
}

/**
 * Formats an amount with an explicit direction sign.
 *
 * Amounts are always stored positive, with the direction carried separately, so
 * the sign has to be reintroduced at display time. Showing a bare "1,250.00" in a
 * ledger is genuinely ambiguous.
 */
export function formatSignedCurrency(amount: number, type: TransactionType): string {
  const sign = type === 'EXPENSE' ? '-' : '+';
  return `${sign}${currencyFormatter.format(amount)}`;
}

/**
 * Parses a `YYYY-MM-DD` calendar date into a local-time Date.
 *
 * This is the trap worth being explicit about: `new Date('2026-08-01')` is parsed
 * by the spec as UTC midnight, so anywhere west of Greenwich it renders as
 * 31 July. A transaction dated the 1st appearing as the 31st is the kind of bug
 * that survives review because it only manifests in some timezones. Splitting the
 * parts and using the local-time constructor keeps the date the user entered.
 */
export function parseDateOnly(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) {
    return null;
  }

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(year, month - 1, day);

  // Rejects impossible dates such as 2026-02-31, which the constructor would
  // silently roll forward into March.
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null;
  }
  return date;
}

const dateFormatter = new Intl.DateTimeFormat(LOCALE, {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
});

const longDateFormatter = new Intl.DateTimeFormat(LOCALE, {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

const dateTimeFormatter = new Intl.DateTimeFormat(LOCALE, {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

/** Formats a `YYYY-MM-DD` transaction date, e.g. "1 Aug 2026". */
export function formatDate(value: string): string {
  const date = parseDateOnly(value);
  return date ? dateFormatter.format(date) : value;
}

export function formatLongDate(value: string): string {
  const date = parseDateOnly(value);
  return date ? longDateFormatter.format(date) : value;
}

/**
 * Formats an ISO-8601 instant such as `createdAt`.
 *
 * Unlike a calendar date these are true timestamps, so the standard Date parser
 * is correct here -- it converts to the viewer's local zone, which is what a
 * "created at" is expected to show.
 */
export function formatDateTime(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateTimeFormatter.format(date);
}

/** Formats a `YYYY-MM` month key from the dashboard, e.g. "Aug 2026". */
export function formatMonth(value: string): string {
  const match = /^(\d{4})-(\d{2})$/.exec(value);
  if (!match) {
    return value;
  }
  const date = new Date(Number(match[1]), Number(match[2]) - 1, 1);
  return new Intl.DateTimeFormat(LOCALE, { month: 'short', year: 'numeric' }).format(date);
}

/** Short month label for chart axes, where the year would crowd the ticks. */
export function formatMonthShort(value: string): string {
  const match = /^(\d{4})-(\d{2})$/.exec(value);
  if (!match) {
    return value;
  }
  const date = new Date(Number(match[1]), Number(match[2]) - 1, 1);
  return new Intl.DateTimeFormat(LOCALE, { month: 'short' }).format(date);
}

export function formatPercent(value: number): string {
  return `${value.toFixed(1)}%`;
}

/** Today as `YYYY-MM-DD` in local time, for date-input defaults and maximums. */
export function todayAsDateOnly(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

/** "ACTIVE" -> "Active". Enum values are shouted; interface labels should not be. */
export function toTitleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

/** Initials for the avatar, e.g. "Abhiram P" -> "AP". */
export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return '?';
  }
  const first = parts[0]?.charAt(0) ?? '';
  const last = parts.length > 1 ? (parts[parts.length - 1]?.charAt(0) ?? '') : '';
  return (first + last).toUpperCase();
}
