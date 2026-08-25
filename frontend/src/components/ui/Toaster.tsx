import type { ReactElement } from 'react';
import styles from './Toaster.module.css';
import { useToast } from '@/hooks/useToast';
import type { ToastVariant } from '@/context/ToastContext';

const ICONS: Record<ToastVariant, ReactElement> = {
  success: (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 6 9 17l-5-5" />
    </svg>
  ),
  error: (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
      <circle cx="12" cy="12" r="10" />
      <path d="M12 8v4M12 16h.01" />
    </svg>
  ),
  info: (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
      <circle cx="12" cy="12" r="10" />
      <path d="M12 16v-4M12 8h.01" />
    </svg>
  ),
};

/**
 * Renders queued toasts.
 *
 * The container carries `role="status"` with `aria-live="polite"`, so new
 * messages are announced without interrupting whatever the user is doing. An
 * assertive region would cut across their current task, which is too aggressive
 * for "Transaction saved".
 *
 * The live region is always mounted, even when empty: a region added to the DOM
 * at the same moment as its content is frequently missed by screen readers, which
 * only watch regions that already existed.
 */
export function Toaster() {
  const { toasts, dismissToast } = useToast();

  return (
    <div className={styles.region} role="status" aria-live="polite" aria-atomic="false">
      {toasts.map((toast) => (
        <div key={toast.id} className={[styles.toast, styles[toast.variant]].join(' ')}>
          <span className={styles.icon} aria-hidden="true">
            {ICONS[toast.variant]}
          </span>
          <span className={styles.message}>{toast.message}</span>
          <button
            type="button"
            className={styles.dismiss}
            onClick={() => dismissToast(toast.id)}
            aria-label="Dismiss notification"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" aria-hidden="true">
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
        </div>
      ))}
    </div>
  );
}
