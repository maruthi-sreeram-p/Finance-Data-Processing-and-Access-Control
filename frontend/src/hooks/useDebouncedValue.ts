import { useEffect, useState } from 'react';

/**
 * Delays a rapidly-changing value until it settles.
 *
 * Used for filter inputs so that typing an amount does not fire a request per
 * keystroke. The delay is deliberately short enough to feel immediate.
 */
export function useDebouncedValue<T>(value: T, delayMs = 350): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    // Clearing on every change is what makes this a debounce rather than a delay:
    // the timer only fires once the value has stopped changing.
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
