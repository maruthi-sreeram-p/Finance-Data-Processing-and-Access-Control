import styles from './Charts.module.css';
import type { MonthlySummary } from '@/types/api';
import { formatCompactCurrency, formatCurrency, formatMonthShort } from '@/utils/format';

interface MonthlyTrendChartProps {
  data: MonthlySummary[];
}

/** Drawn in a fixed coordinate space and scaled by the viewBox, so it stays crisp at any width. */
const WIDTH = 640;
const HEIGHT = 260;
const PADDING = { top: 16, right: 8, bottom: 32, left: 56 };

const PLOT_WIDTH = WIDTH - PADDING.left - PADDING.right;
const PLOT_HEIGHT = HEIGHT - PADDING.top - PADDING.bottom;

/**
 * Rounds an axis maximum up to a readable number.
 *
 * Without this the top gridline lands on a value like 4,873, which tells a reader
 * nothing. Snapping to 1/2/5 x 10^n produces the round numbers people expect on
 * an axis.
 */
function niceMaximum(value: number): number {
  if (value <= 0) {
    return 1;
  }
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const normalized = value / magnitude;

  const step = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return step * magnitude;
}

/**
 * Income against expenses, month by month.
 *
 * Grouped bars rather than stacked: the question this chart answers is "did I
 * spend more than I earned", and side-by-side pairs make that comparison direct.
 * Stacking would show a combined total, which is not a meaningful quantity here.
 *
 * Every value comes from `monthlySummary` in the dashboard response, aggregated
 * by the database. Nothing is computed or invented in the browser.
 */
export function MonthlyTrendChart({ data }: MonthlyTrendChartProps) {
  const peak = Math.max(...data.map((month) => Math.max(month.income, month.expenses)), 0);
  const axisMax = niceMaximum(peak);

  const groupWidth = PLOT_WIDTH / data.length;
  // Bars occupy 62% of their slot, leaving a visible gutter between months.
  const barWidth = Math.min(22, (groupWidth * 0.62) / 2);
  const gap = 3;

  const ticks = [0, 0.25, 0.5, 0.75, 1].map((fraction) => axisMax * fraction);

  function yFor(value: number): number {
    return PADDING.top + PLOT_HEIGHT - (value / axisMax) * PLOT_HEIGHT;
  }

  return (
    <div className={styles.wrap}>
      <svg
        className={styles.svg}
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        preserveAspectRatio="xMidYMid meet"
        role="img"
        // A chart is invisible to a screen reader; this sentence is the chart for
        // that user, so it states the actual figures rather than "a bar chart".
        aria-label={`Income and expenses by month. ${data
          .map(
            (month) =>
              `${formatMonthShort(month.month)}: income ${formatCurrency(month.income)}, expenses ${formatCurrency(month.expenses)}`,
          )
          .join('. ')}`}
      >
        {/* Horizontal gridlines, drawn behind the bars. */}
        {ticks.map((tick) => (
          <g key={tick}>
            <line
              className={styles.gridLine}
              x1={PADDING.left}
              y1={yFor(tick)}
              x2={WIDTH - PADDING.right}
              y2={yFor(tick)}
            />
            <text className={styles.axisLabel} x={PADDING.left - 8} y={yFor(tick) + 4} textAnchor="end">
              {tick === 0 ? '0' : formatCompactCurrency(tick)}
            </text>
          </g>
        ))}

        <line
          className={styles.axisLine}
          x1={PADDING.left}
          y1={PADDING.top + PLOT_HEIGHT}
          x2={WIDTH - PADDING.right}
          y2={PADDING.top + PLOT_HEIGHT}
        />

        {data.map((month, index) => {
          const centre = PADDING.left + groupWidth * index + groupWidth / 2;
          const incomeHeight = (month.income / axisMax) * PLOT_HEIGHT;
          const expenseHeight = (month.expenses / axisMax) * PLOT_HEIGHT;

          return (
            <g key={month.month}>
              {/* A zero-value month still draws a 2px stub, so an empty month
                  reads as "nothing here" rather than as missing data. */}
              <rect
                className={`${styles.bar} ${styles.barIncome}`}
                x={centre - barWidth - gap / 2}
                y={yFor(month.income)}
                width={barWidth}
                height={Math.max(incomeHeight, month.income > 0 ? 2 : 0)}
                rx="3"
              >
                <title>{`${formatMonthShort(month.month)} income: ${formatCurrency(month.income)}`}</title>
              </rect>

              <rect
                className={`${styles.bar} ${styles.barExpense}`}
                x={centre + gap / 2}
                y={yFor(month.expenses)}
                width={barWidth}
                height={Math.max(expenseHeight, month.expenses > 0 ? 2 : 0)}
                rx="3"
              >
                <title>{`${formatMonthShort(month.month)} expenses: ${formatCurrency(month.expenses)}`}</title>
              </rect>

              <text
                className={styles.axisLabel}
                x={centre}
                y={HEIGHT - PADDING.bottom + 20}
                textAnchor="middle"
              >
                {formatMonthShort(month.month)}
              </text>
            </g>
          );
        })}
      </svg>

      <div className={styles.legend}>
        <span className={styles.legendItem}>
          <span className={styles.legendSwatch} style={{ background: 'var(--positive-500)' }} />
          Income
        </span>
        <span className={styles.legendItem}>
          <span className={styles.legendSwatch} style={{ background: 'var(--negative-500)' }} />
          Expenses
        </span>
      </div>
    </div>
  );
}
