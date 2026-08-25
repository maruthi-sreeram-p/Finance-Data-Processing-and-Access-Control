import styles from './Pagination.module.css';

interface PaginationProps {
  /** Zero-based, matching the backend. */
  page: number;
  totalPages: number;
  totalElements: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  /** Noun for the summary line, e.g. "transactions". */
  itemLabel?: string;
}

/**
 * Builds the list of page numbers to show, collapsing long runs with ellipses.
 *
 * Always shows the first and last page plus a window around the current one, so
 * the control stays a fixed width whether there are 5 pages or 500. Rendering
 * every page number is fine at 5 and unusable at 500.
 */
function buildPageList(current: number, total: number): (number | 'gap')[] {
  const MAX_SHOWN = 7;

  if (total <= MAX_SHOWN) {
    return Array.from({ length: total }, (_, index) => index);
  }

  const pages = new Set<number>([0, total - 1, current]);

  // One neighbour either side of the current page.
  if (current - 1 > 0) pages.add(current - 1);
  if (current + 1 < total - 1) pages.add(current + 1);

  // Near either end, extend the window so the control does not shrink there.
  if (current <= 2) {
    pages.add(1);
    pages.add(2);
    pages.add(3);
  }
  if (current >= total - 3) {
    pages.add(total - 2);
    pages.add(total - 3);
    pages.add(total - 4);
  }

  const sorted = [...pages].filter((page) => page >= 0 && page < total).sort((a, b) => a - b);

  const result: (number | 'gap')[] = [];
  let previous = -1;
  for (const page of sorted) {
    if (previous >= 0 && page - previous > 1) {
      result.push('gap');
    }
    result.push(page);
    previous = page;
  }
  return result;
}

/**
 * Page navigation for a paginated listing.
 *
 * Page indices are zero-based internally, matching the API, but displayed
 * one-based because "page 0" is meaningless to a reader. Mixing the two up is a
 * classic off-by-one, so the conversion happens in exactly one place: the label.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
  itemLabel = 'items',
}: PaginationProps) {
  // A single page needs no controls, but the count is still useful context.
  const showControls = totalPages > 1;

  const firstItem = totalElements === 0 ? 0 : page * pageSize + 1;
  const lastItem = Math.min((page + 1) * pageSize, totalElements);

  return (
    <div className={styles.wrap}>
      <p className={styles.summary}>
        {totalElements === 0
          ? `No ${itemLabel}`
          : `Showing ${firstItem}–${lastItem} of ${totalElements} ${itemLabel}`}
      </p>

      {showControls && (
        <nav className={styles.controls} aria-label="Pagination">
          <button
            type="button"
            className={styles.page}
            onClick={() => onPageChange(page - 1)}
            disabled={page === 0}
            aria-label="Previous page"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="m15 18-6-6 6-6" />
            </svg>
          </button>

          {buildPageList(page, totalPages).map((entry, index) =>
            entry === 'gap' ? (
              <span key={`gap-${index}`} className={styles.ellipsis} aria-hidden="true">
                …
              </span>
            ) : (
              <button
                key={entry}
                type="button"
                className={[styles.page, entry === page ? styles.current : ''].filter(Boolean).join(' ')}
                onClick={() => onPageChange(entry)}
                // Tells assistive technology which page is the current one, rather
                // than relying on the highlight alone.
                aria-current={entry === page ? 'page' : undefined}
                aria-label={`Page ${entry + 1}`}
              >
                {entry + 1}
              </button>
            ),
          )}

          <button
            type="button"
            className={styles.page}
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages - 1}
            aria-label="Next page"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="m9 18 6-6-6-6" />
            </svg>
          </button>
        </nav>
      )}
    </div>
  );
}
