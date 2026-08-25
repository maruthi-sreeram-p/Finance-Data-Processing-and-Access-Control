import styles from './Spinner.module.css';

interface SpinnerProps {
  /** Diameter in pixels. */
  size?: number;
  /** Inherits currentColor, so it takes the colour of whatever contains it. */
  className?: string;
}

export function Spinner({ size = 20, className }: SpinnerProps) {
  const borderWidth = Math.max(2, Math.round(size / 9));

  return (
    <span
      className={[styles.spinner, className ?? ''].filter(Boolean).join(' ')}
      style={{ width: size, height: size, borderWidth }}
      // Decorative: the surrounding region announces the loading state, so
      // announcing it here as well would be duplicate noise.
      aria-hidden="true"
    />
  );
}

interface LoadingStateProps {
  label?: string;
}

/**
 * A centred spinner with a label, for a whole panel that is still loading.
 *
 * `role="status"` means a screen reader announces the label when it appears, so
 * the wait is communicated rather than being a purely visual event.
 */
export function LoadingState({ label = 'Loading…' }: LoadingStateProps) {
  return (
    <div className={styles.centered} role="status">
      <Spinner size={28} />
      <span className={styles.label}>{label}</span>
    </div>
  );
}
