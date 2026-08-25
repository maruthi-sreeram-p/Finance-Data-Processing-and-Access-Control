import { useEffect } from 'react';

const BASE_TITLE = 'FinTrack';

/**
 * Sets the browser tab title per page.
 *
 * Not decoration: the title is what a screen reader announces on navigation and
 * what distinguishes entries in browser history and open tabs.
 */
export function useDocumentTitle(title?: string): void {
  useEffect(() => {
    document.title = title ? `${title} · ${BASE_TITLE}` : BASE_TITLE;
  }, [title]);
}
