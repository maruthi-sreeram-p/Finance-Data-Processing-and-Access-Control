import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

export type ToastVariant = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  variant: ToastVariant;
  message: string;
}

export interface ToastContextValue {
  toasts: Toast[];
  showToast: (message: string, variant?: ToastVariant) => void;
  showSuccess: (message: string) => void;
  showError: (message: string) => void;
  dismissToast: (id: number) => void;
}

export const ToastContext = createContext<ToastContextValue | null>(null);

/** Long enough to read a sentence, short enough not to linger over the content. */
const AUTO_DISMISS_MS = 5_000;

/** Beyond this the stack covers the interface it is reporting on. */
const MAX_VISIBLE = 3;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  // Monotonic ids. Date.now() would collide for two toasts raised in the same
  // millisecond -- easy to hit when a failed save reports more than one problem.
  const nextId = useRef(0);

  // Tracked so every pending dismissal can be cancelled on unmount; otherwise a
  // timer fires against an unmounted component after navigation.
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  const dismissToast = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));

    const timer = timers.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timers.current.delete(id);
    }
  }, []);

  const showToast = useCallback(
    (message: string, variant: ToastVariant = 'info') => {
      const id = nextId.current++;

      setToasts((current) => {
        const next = [...current, { id, variant, message }];
        // Oldest fall off the top rather than the newest being suppressed: the
        // most recent message is the one describing what just happened.
        return next.slice(-MAX_VISIBLE);
      });

      timers.current.set(
        id,
        setTimeout(() => dismissToast(id), AUTO_DISMISS_MS),
      );
    },
    [dismissToast],
  );

  const showSuccess = useCallback((message: string) => showToast(message, 'success'), [showToast]);
  const showError = useCallback((message: string) => showToast(message, 'error'), [showToast]);

  useEffect(() => {
    const pending = timers.current;
    return () => {
      pending.forEach(clearTimeout);
      pending.clear();
    };
  }, []);

  const value = useMemo(
    () => ({ toasts, showToast, showSuccess, showError, dismissToast }),
    [toasts, showToast, showSuccess, showError, dismissToast],
  );

  return <ToastContext.Provider value={value}>{children}</ToastContext.Provider>;
}
