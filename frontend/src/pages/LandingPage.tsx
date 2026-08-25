import { Link, Navigate } from 'react-router-dom';
import styles from './LandingPage.module.css';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/hooks/useAuth';
import { useTheme } from '@/hooks/useTheme';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import {
  IconChart,
  IconMoon,
  IconReceipt,
  IconShield,
  IconSun,
  IconTag,
  IconUsers,
  IconWallet,
} from '@/components/ui/Icons';

/**
 * Feature copy.
 *
 * Every claim here describes something the application actually does. There is no
 * "bank-level encryption" or invented compliance badge — the honest version is
 * more persuasive in a portfolio piece anyway, because each line is something a
 * reader can go and verify in the code.
 */
const FEATURES = [
  {
    icon: <IconReceipt size={20} />,
    title: 'Income and expense tracking',
    copy: 'Record what comes in and what goes out, each filed under a category. Amounts are stored as exact decimals, never floating point.',
  },
  {
    icon: <IconChart size={20} />,
    title: 'Dashboard analytics',
    copy: 'Totals, balance, category breakdown and a month-by-month trend — all aggregated by the database, so the figures stay right as your history grows.',
  },
  {
    icon: <IconShield size={20} />,
    title: 'Ownership enforced server-side',
    copy: 'Every request is checked against the record it touches. Changing an id in the URL returns the same “not found” as a record that never existed.',
  },
  {
    icon: <IconUsers size={20} />,
    title: 'Role-based access control',
    copy: 'Three roles with distinct privileges. Read access widens with role; write access never does — nobody can alter another person’s records.',
  },
  {
    icon: <IconTag size={20} />,
    title: 'Category rules that hold',
    copy: 'Categories know which direction they accept, so a salary cannot be filed under Rent and quietly corrupt your totals.',
  },
  {
    icon: <IconWallet size={20} />,
    title: 'Filter and search your ledger',
    copy: 'Narrow by type, category, date range or amount, with pagination and sorting that stay stable across pages.',
  },
];

export function LandingPage() {
  useDocumentTitle();

  const { isAuthenticated, initializing } = useAuth();
  const { theme, toggleTheme } = useTheme();

  // A signed-in visitor arriving at the marketing page wants their dashboard, not
  // a pitch for the product they already use.
  if (!initializing && isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <Link to="/" className={styles.brand}>
          <span className={styles.brandMark} aria-hidden="true">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 17l6-6 4 4 8-8" />
              <path d="M17 7h4v4" />
            </svg>
          </span>
          <span className={styles.brandName}>FinTrack</span>
        </Link>

        <div className={styles.headerActions}>
          <Button
            variant="ghost"
            iconOnly
            onClick={toggleTheme}
            aria-label={`Switch to ${theme === 'light' ? 'dark' : 'light'} theme`}
          >
            {theme === 'light' ? <IconMoon /> : <IconSun />}
          </Button>
          <Link to="/login">
            <Button variant="ghost">Log in</Button>
          </Link>
          <Link to="/register">
            <Button>Get started</Button>
          </Link>
        </div>
      </header>

      <main className={styles.main}>
        <section className={`${styles.section} ${styles.hero}`}>
          <div className={styles.heroText}>
            <span className={styles.eyebrow}>
              <IconShield size={14} />
              Personal finance, properly secured
            </span>

            <h1 className={styles.heroTitle}>
              Know where your money <span className={styles.heroAccent}>actually goes</span>.
            </h1>

            <p className={styles.heroCopy}>
              FinTrack turns a list of transactions into an answer. Record income and
              expenses, categorise them, and see totals, trends and category breakdowns
              computed from your own ledger — never estimated, never sampled.
            </p>

            <div className={styles.heroActions}>
              <Link to="/register">
                <Button size="lg">Create free account</Button>
              </Link>
              <Link to="/login">
                <Button size="lg" variant="secondary">
                  Log in
                </Button>
              </Link>
            </div>

            <p className={styles.heroNote}>
              No card required. Your ledger is visible only to you.
            </p>
          </div>

          {/*
            An illustrative preview of the dashboard's shape, not live data — it is
            rendered for visitors who are not signed in and therefore have no ledger
            to show. The proportions are fixed decoration, which is why it carries no
            currency figures that could be mistaken for a real balance.
          */}
          <div className={styles.preview} aria-hidden="true">
            <div className={styles.previewHead}>
              <span className={styles.previewTitle}>Dashboard overview</span>
              <IconChart size={16} />
            </div>

            <div className={styles.previewStats}>
              <div className={styles.previewStat}>
                <span className={styles.previewStatLabel}>Income</span>
                <span className={`${styles.previewStatValue} ${styles.previewPositive}`}>▲</span>
              </div>
              <div className={styles.previewStat}>
                <span className={styles.previewStatLabel}>Expenses</span>
                <span className={`${styles.previewStatValue} ${styles.previewNegative}`}>▼</span>
              </div>
              <div className={styles.previewStat}>
                <span className={styles.previewStatLabel}>Balance</span>
                <span className={styles.previewStatValue}>—</span>
              </div>
            </div>

            <div className={styles.previewBars}>
              {[42, 68, 35, 80, 55, 92, 60].map((height, index) => (
                <span
                  key={index}
                  className={`${styles.previewBar} ${index % 2 === 1 ? styles.previewBarAlt : ''}`}
                  style={{ height: `${height}%` }}
                />
              ))}
            </div>

            <div className={styles.previewRows}>
              {[
                ['Rent', 72],
                ['Food', 46],
                ['Transport', 28],
              ].map(([label, width]) => (
                <div key={label as string} className={styles.previewRow}>
                  <span className={styles.previewRowLabel}>{label}</span>
                  <span className={styles.previewRowBar}>
                    <span className={styles.previewRowFill} style={{ width: `${width}%` }} />
                  </span>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className={`${styles.section} ${styles.features}`}>
          <div className={styles.sectionHead}>
            <h2 className={styles.sectionTitle}>Built like a real financial system</h2>
            <p className={styles.sectionCopy}>
              Not a to-do list with a currency symbol. Exact decimal arithmetic,
              database-side aggregation, and authorization checked on every request.
            </p>
          </div>

          <ul className={styles.featureGrid}>
            {FEATURES.map((feature) => (
              <li key={feature.title} className={styles.feature}>
                <span className={styles.featureIcon} aria-hidden="true">
                  {feature.icon}
                </span>
                <h3 className={styles.featureTitle}>{feature.title}</h3>
                <p className={styles.featureCopy}>{feature.copy}</p>
              </li>
            ))}
          </ul>
        </section>

        <section className={`${styles.section} ${styles.cta}`}>
          <div className={styles.ctaInner}>
            <h2 className={styles.ctaTitle}>Start tracking in two minutes</h2>
            <p className={styles.ctaCopy}>
              Create an account, add your first transaction, and your dashboard fills in
              immediately.
            </p>
            <Link to="/register">
              <Button size="lg" variant="secondary">
                Create your account
              </Button>
            </Link>
          </div>
        </section>
      </main>

      <footer className={styles.footer}>
        FinTrack — a Spring Boot and React finance platform.
      </footer>
    </div>
  );
}
