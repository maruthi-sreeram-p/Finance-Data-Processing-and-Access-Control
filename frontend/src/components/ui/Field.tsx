import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import styles from './Field.module.css';

interface BaseFieldProps {
  label: string;
  /** Message from client validation or the backend's fieldErrors. */
  error?: string;
  hint?: string;
  required?: boolean;
}

/**
 * Wraps a control with its label, hint and error.
 *
 * The wiring here is the accessibility-critical part and the reason these are not
 * hand-assembled per form:
 *
 *  - `htmlFor`/`id` are generated with `useId`, so the label is programmatically
 *    tied to its input. A visually adjacent label that is not associated is not a
 *    label at all to a screen reader, and clicking it does not focus the field.
 *  - `aria-describedby` points at the hint and error, so their text is announced
 *    with the field rather than being noticed only if the user happens to look.
 *  - `aria-invalid` communicates the failure state independently of the red
 *    border, which conveys nothing to a non-sighted user.
 */
function useFieldIds(error?: string, hint?: string) {
  const id = useId();
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;

  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');

  return { id, errorId, hintId, describedBy: describedBy || undefined };
}

function FieldShell({
  label,
  error,
  hint,
  required,
  id,
  errorId,
  hintId,
  children,
}: BaseFieldProps & {
  id: string;
  errorId: string;
  hintId: string;
  children: ReactNode;
}) {
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
        {required && (
          <span className={styles.required} aria-hidden="true">
            *
          </span>
        )}
        {required && <span className="sr-only">(required)</span>}
      </label>

      {children}

      {hint && !error && (
        <span className={styles.hint} id={hintId}>
          {hint}
        </span>
      )}

      {/* role="alert" so the message is announced the moment it appears, rather
          than only when the user next moves focus into the field. */}
      {error && (
        <span className={styles.error} id={errorId} role="alert">
          {error}
        </span>
      )}
    </div>
  );
}

type TextFieldProps = BaseFieldProps &
  Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> & { prefix?: string };

export function TextField({ label, error, hint, required, prefix, className, ...rest }: TextFieldProps) {
  const { id, errorId, hintId, describedBy } = useFieldIds(error, hint);

  const input = (
    <input
      id={id}
      className={[styles.control, error ? styles.invalid : '', prefix ? styles.withPrefix : '', className ?? '']
        .filter(Boolean)
        .join(' ')}
      aria-invalid={error ? true : undefined}
      aria-describedby={describedBy}
      required={required}
      {...rest}
    />
  );

  return (
    <FieldShell label={label} error={error} hint={hint} required={required} id={id} errorId={errorId} hintId={hintId}>
      {prefix ? (
        <span className={styles.prefixWrap}>
          <span className={styles.prefix} aria-hidden="true">
            {prefix}
          </span>
          {input}
        </span>
      ) : (
        input
      )}
    </FieldShell>
  );
}

type SelectFieldProps = BaseFieldProps &
  Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id'> & { children: ReactNode };

export function SelectField({ label, error, hint, required, children, className, ...rest }: SelectFieldProps) {
  const { id, errorId, hintId, describedBy } = useFieldIds(error, hint);

  return (
    <FieldShell label={label} error={error} hint={hint} required={required} id={id} errorId={errorId} hintId={hintId}>
      <select
        id={id}
        className={[styles.control, styles.select, error ? styles.invalid : '', className ?? '']
          .filter(Boolean)
          .join(' ')}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        required={required}
        {...rest}
      >
        {children}
      </select>
    </FieldShell>
  );
}

type TextAreaFieldProps = BaseFieldProps & Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'>;

export function TextAreaField({ label, error, hint, required, className, ...rest }: TextAreaFieldProps) {
  const { id, errorId, hintId, describedBy } = useFieldIds(error, hint);

  return (
    <FieldShell label={label} error={error} hint={hint} required={required} id={id} errorId={errorId} hintId={hintId}>
      <textarea
        id={id}
        className={[styles.control, styles.textarea, error ? styles.invalid : '', className ?? '']
          .filter(Boolean)
          .join(' ')}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        required={required}
        {...rest}
      />
    </FieldShell>
  );
}
