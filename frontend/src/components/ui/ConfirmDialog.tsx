import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import styles from './ConfirmDialog.module.css';
import { Button } from './Button';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string;
  /** Extra context, e.g. a summary of the record being deleted. */
  detail?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Renders the confirm button in red, for irreversible actions. */
  destructive?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * A modal confirmation, used before anything irreversible.
 *
 * Financial records are never deleted without one: an accidental click that
 * silently removes a transaction destroys data the user cannot reconstruct.
 *
 * The accessibility work here is what separates a real dialog from a styled div:
 *
 *  - Rendered through a portal, so it escapes any parent with `overflow: hidden`
 *    or a stacking context that would clip or bury it.
 *  - Focus moves to the cancel button on open and returns to the trigger on
 *    close. Without the return, a keyboard user is dumped back at the top of the
 *    document with no idea where they are.
 *  - Focus is trapped: Tab cycles within the dialog rather than wandering into
 *    the inert page behind it.
 *  - Escape cancels, which is what every user expects a modal to honour.
 *  - Cancel is the default focus, not confirm, so a stray Enter does not delete.
 *  - Background scrolling is locked while open.
 */
export function ConfirmDialog({
  open,
  title,
  message,
  detail,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  destructive = false,
  loading = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const titleId = useId();
  const messageId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const previouslyFocused = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }

    previouslyFocused.current = document.activeElement as HTMLElement | null;
    cancelRef.current?.focus();

    const { overflow } = document.body.style;
    document.body.style.overflow = 'hidden';

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault();
        onCancel();
        return;
      }

      if (event.key !== 'Tab' || !dialogRef.current) {
        return;
      }

      const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      );
      if (focusable.length === 0) {
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!first || !last) {
        return;
      }

      // Wrap at both ends so focus never escapes into the page behind.
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = overflow;
      // Returns the user to whatever they activated, preserving their place.
      previouslyFocused.current?.focus();
    };
  }, [open, onCancel]);

  if (!open) {
    return null;
  }

  return createPortal(
    <div
      className={styles.backdrop}
      // Clicking the backdrop cancels, matching the platform convention. The
      // target check stops a click inside the dialog from bubbling up and closing it.
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onCancel();
        }
      }}
    >
      <div
        ref={dialogRef}
        className={styles.dialog}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={messageId}
      >
        <h2 className={styles.title} id={titleId}>
          {title}
        </h2>
        <p className={styles.message} id={messageId}>
          {message}
        </p>

        {detail && <div className={styles.detail}>{detail}</div>}

        <div className={styles.actions}>
          <Button ref={cancelRef} variant="secondary" onClick={onCancel} disabled={loading}>
            {cancelLabel}
          </Button>
          <Button
            variant={destructive ? 'danger' : 'primary'}
            onClick={onConfirm}
            loading={loading}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
