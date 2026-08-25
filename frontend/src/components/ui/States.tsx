import type { ReactNode } from 'react';
import styles from './States.module.css';

interface EmptyStateProps {
  title: string;
  description?: string;
  /** A call to action, so an empty screen suggests the next step rather than dead-ending. */
  action?: ReactNode;
  icon?: ReactNode;
}

/**
 * Shown when a request succeeded but returned nothing.
 *
 * Deliberately distinct from the error state: "you have no transactions yet" and
 * "we could not load your transactions" call for completely different reactions,
 * and a blank panel says neither.
 */
export function EmptyState({ title, description, action, icon }: EmptyStateProps) {
  return (
    <div className={styles.empty}>
      {icon && (
        <span className={styles.icon} aria-hidden="true">
          {icon}
        </span>
      )}
      <p className={styles.title}>{title}</p>
      {description && <p className={styles.description}>{description}</p>}
      {action && <div className={styles.action}>{action}</div>}
    </div>
  );
}

interface ErrorMessageProps {
  message: string;
  /** Offers a retry when the failure might be transient. */
  action?: ReactNode;
}

/**
 * A failed request.
 *
 * `role="alert"` so it is announced immediately; a visually obvious red panel is
 * invisible to a screen reader user who has not moved focus into it.
 */
export function ErrorMessage({ message, action }: ErrorMessageProps) {
  return (
    <div className={styles.error} role="alert">
      <svg
        className={styles.errorIcon}
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="10" />
        <path d="M12 8v4M12 16h.01" />
      </svg>
      <div className={styles.errorBody}>
        <span className={styles.errorText}>{message}</span>
        {action}
      </div>
    </div>
  );
}

interface SkeletonProps {
  width?: string | number;
  height?: string | number;
  radius?: string;
}

export function Skeleton({ width = '100%', height = 16, radius }: SkeletonProps) {
  return (
    <span
      className={styles.skeleton}
      style={{ width, height, borderRadius: radius, display: 'block' }}
      aria-hidden="true"
    />
  );
}
