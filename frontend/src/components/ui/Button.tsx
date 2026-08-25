import type { ButtonHTMLAttributes, ReactNode, Ref } from 'react';
import styles from './Button.module.css';
import { Spinner } from './Spinner';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'dangerGhost';
export type ButtonSize = 'sm' | 'md' | 'lg';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Shows a spinner and blocks further clicks. */
  loading?: boolean;
  fullWidth?: boolean;
  /** Square padding for a button whose only child is an icon. Requires aria-label. */
  iconOnly?: boolean;
  children?: ReactNode;
  /**
   * React 19 passes `ref` to function components as an ordinary prop, so no
   * forwardRef wrapper is needed -- it just has to be declared to be typed.
   * Used by the confirm dialog to move focus onto its cancel button.
   */
  ref?: Ref<HTMLButtonElement>;
}

/**
 * The application's button.
 *
 * Worth noting two behaviours it standardizes:
 *
 *  - `loading` also sets `disabled`, so a slow save cannot be submitted twice by
 *    an impatient double click. Getting this wrong creates duplicate records.
 *  - `type` defaults to `"button"`, not the HTML default of `"submit"`. A button
 *    dropped inside a form without an explicit type silently submits it, which is
 *    a genuinely confusing bug to track down.
 */
export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  fullWidth = false,
  iconOnly = false,
  disabled,
  children,
  className,
  type = 'button',
  ref,
  ...rest
}: ButtonProps) {
  const classes = [
    styles.button,
    styles[variant],
    styles[size],
    fullWidth ? styles.fullWidth : '',
    iconOnly ? styles.iconOnly : '',
    loading ? styles.relative : '',
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button
      ref={ref}
      type={type}
      className={classes}
      disabled={disabled || loading}
      // Tells assistive technology the control is working, rather than leaving a
      // screen reader user with silence after they activated it.
      aria-busy={loading || undefined}
      {...rest}
    >
      <span className={loading ? styles.loadingLabel : undefined}>{children}</span>
      {loading && (
        <span className={styles.spinnerOverlay} aria-hidden="true">
          <Spinner size={size === 'lg' ? 20 : 16} />
        </span>
      )}
    </button>
  );
}
