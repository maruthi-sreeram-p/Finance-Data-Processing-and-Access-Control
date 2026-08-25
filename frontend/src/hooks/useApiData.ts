import { useCallback, useEffect, useRef, useState } from 'react';
import { AppError, toAppError } from '@/services/apiError';

export interface ApiDataState<T> {
  data: T | null;
  loading: boolean;
  error: AppError | null;
  /** Re-runs the request, e.g. after a mutation elsewhere on the page. */
  reload: () => void;
}

/**
 * Runs an async request and tracks its loading and error state.
 *
 * Exists so that no page reimplements the same three `useState` calls and the
 * same forgotten cleanup. Two details it gets right that hand-rolled versions
 * usually do not:
 *
 *  - **Stale responses are discarded.** If the inputs change while a request is in
 *    flight, the older response is ignored when it lands. Without this, changing a
 *    filter twice quickly can leave the screen showing the first result, because
 *    responses do not necessarily arrive in the order they were sent.
 *  - **No state is set after unmount**, which would otherwise warn and leak.
 *
 * @param request the call to run; must be stable, so wrap it in useCallback
 * @param enabled skip the request entirely while false
 */
export function useApiData<T>(request: () => Promise<T>, enabled = true): ApiDataState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<AppError | null>(null);

  // Incremented per run; only the newest run is allowed to write state.
  const runId = useRef(0);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const execute = useCallback(async () => {
    if (!enabled) {
      setLoading(false);
      return;
    }

    const currentRun = ++runId.current;
    setLoading(true);
    setError(null);

    try {
      const result = await request();
      if (mounted.current && currentRun === runId.current) {
        setData(result);
      }
    } catch (caught) {
      if (mounted.current && currentRun === runId.current) {
        setError(toAppError(caught));
        // The previous data is deliberately left in place: replacing a populated
        // table with an empty one on a transient failure loses the user's context
        // for no benefit. The error is shown alongside it.
      }
    } finally {
      if (mounted.current && currentRun === runId.current) {
        setLoading(false);
      }
    }
  }, [request, enabled]);

  useEffect(() => {
    void execute();
  }, [execute]);

  return { data, loading, error, reload: execute };
}
