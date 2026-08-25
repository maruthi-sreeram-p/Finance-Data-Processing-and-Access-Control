import styles from './Charts.module.css';
import { formatCurrency } from '@/utils/format';

interface IncomeExpenseDonutProps {
  income: number;
  expenses: number;
  balance: number;
}

const SIZE = 180;
const STROKE = 22;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/**
 * Income against expenses as a single split ring.
 *
 * A donut is a poor choice for many categories -- angles are hard to compare --
 * but it is a good one for exactly two parts, where the reader only needs to see
 * which is larger and roughly by how much. The precise figures are in the legend
 * beside it, so nobody has to estimate from the arc.
 *
 * The arcs are drawn with `stroke-dasharray` on two circles rather than with path
 * arithmetic: it is far less code, and it cannot produce the malformed arc that a
 * hand-computed `A` command gives at exactly 0% or 100%.
 */
export function IncomeExpenseDonut({ income, expenses, balance }: IncomeExpenseDonutProps) {
  const total = income + expenses;

  // An empty ledger has no proportions to draw. Splitting 0/0 would be NaN, so
  // the ring renders neutral and the legend still reports the (zero) figures.
  const incomeFraction = total > 0 ? income / total : 0;
  const expenseFraction = total > 0 ? expenses / total : 0;

  const incomeLength = CIRCUMFERENCE * incomeFraction;
  const expenseLength = CIRCUMFERENCE * expenseFraction;

  return (
    <div className={styles.donutWrap}>
      <svg
        className={styles.donutSvg}
        width={SIZE}
        height={SIZE}
        viewBox={`0 0 ${SIZE} ${SIZE}`}
        role="img"
        aria-label={`Income ${formatCurrency(income)}, expenses ${formatCurrency(expenses)}, balance ${formatCurrency(balance)}`}
      >
        {/* Rotated so the arcs begin at twelve o'clock rather than three. */}
        <g transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}>
          <circle
            cx={SIZE / 2}
            cy={SIZE / 2}
            r={RADIUS}
            fill="none"
            stroke="var(--bg-sunken)"
            strokeWidth={STROKE}
          />

          {income > 0 && (
            <circle
              cx={SIZE / 2}
              cy={SIZE / 2}
              r={RADIUS}
              fill="none"
              stroke="var(--positive-500)"
              strokeWidth={STROKE}
              strokeDasharray={`${incomeLength} ${CIRCUMFERENCE - incomeLength}`}
              strokeLinecap="butt"
            />
          )}

          {expenses > 0 && (
            <circle
              cx={SIZE / 2}
              cy={SIZE / 2}
              r={RADIUS}
              fill="none"
              stroke="var(--negative-500)"
              strokeWidth={STROKE}
              strokeDasharray={`${expenseLength} ${CIRCUMFERENCE - expenseLength}`}
              // Offset by the income arc so the expense arc starts where it ends.
              strokeDashoffset={-incomeLength}
              strokeLinecap="butt"
            />
          )}
        </g>

        <text
          className={styles.donutCenterLabel}
          x={SIZE / 2}
          y={SIZE / 2 - 6}
          textAnchor="middle"
        >
          Balance
        </text>
        <text
          className={styles.donutCenterValue}
          x={SIZE / 2}
          y={SIZE / 2 + 14}
          textAnchor="middle"
          fill={balance < 0 ? 'var(--negative-text)' : 'var(--text-primary)'}
        >
          {formatCurrency(balance)}
        </text>
      </svg>

      <div className={styles.donutLegend}>
        <div className={styles.donutLegendItem}>
          <span className={styles.legendSwatch} style={{ background: 'var(--positive-500)' }} />
          <span className={styles.donutLegendText}>
            <span className={styles.donutLegendLabel}>{formatCurrency(income)}</span>
            <span className={styles.donutLegendValue}>
              Income{total > 0 ? ` · ${(incomeFraction * 100).toFixed(1)}%` : ''}
            </span>
          </span>
        </div>

        <div className={styles.donutLegendItem}>
          <span className={styles.legendSwatch} style={{ background: 'var(--negative-500)' }} />
          <span className={styles.donutLegendText}>
            <span className={styles.donutLegendLabel}>{formatCurrency(expenses)}</span>
            <span className={styles.donutLegendValue}>
              Expenses{total > 0 ? ` · ${(expenseFraction * 100).toFixed(1)}%` : ''}
            </span>
          </span>
        </div>
      </div>
    </div>
  );
}
