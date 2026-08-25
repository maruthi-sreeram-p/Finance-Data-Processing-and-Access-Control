import type { ReactNode } from 'react';
import styles from './Badge.module.css';
import type { CategoryType, Role, TransactionType, UserStatus } from '@/types/api';
import { toTitleCase } from '@/utils/format';

export type BadgeTone = 'neutral' | 'positive' | 'negative' | 'warning' | 'accent';

interface BadgeProps {
  tone?: BadgeTone;
  /** Adds a coloured dot, so status is not conveyed by colour alone. */
  dot?: boolean;
  children: ReactNode;
}

export function Badge({ tone = 'neutral', dot = false, children }: BadgeProps) {
  return (
    <span className={[styles.badge, styles[tone]].join(' ')}>
      {dot && <span className={styles.dot} aria-hidden="true" />}
      {children}
    </span>
  );
}

/**
 * Transaction direction.
 *
 * Green and red are conventional, but the word is always present too: roughly one
 * man in twelve cannot reliably distinguish the two, and a ledger that encodes
 * income versus expense in hue alone is unreadable to them.
 */
export function TypeBadge({ type }: { type: TransactionType }) {
  return (
    <Badge tone={type === 'INCOME' ? 'positive' : 'negative'} dot>
      {toTitleCase(type)}
    </Badge>
  );
}

export function StatusBadge({ status }: { status: UserStatus }) {
  const tone: BadgeTone =
    status === 'ACTIVE' ? 'positive' : status === 'SUSPENDED' ? 'negative' : 'neutral';

  return (
    <Badge tone={tone} dot>
      {toTitleCase(status)}
    </Badge>
  );
}

export function RoleBadge({ role }: { role: Role }) {
  const tone: BadgeTone = role === 'ADMIN' ? 'accent' : role === 'MANAGER' ? 'warning' : 'neutral';
  return <Badge tone={tone}>{toTitleCase(role)}</Badge>;
}

/** Category chip. Neutral by design: colouring every category would compete with
    the income/expense signal, which is the one that carries meaning. */
export function CategoryBadge({ name, type }: { name: string; type?: CategoryType }) {
  return (
    <Badge tone="neutral">
      {name}
      {type === 'BOTH' && <span className="sr-only"> (usable for income or expenses)</span>}
    </Badge>
  );
}
