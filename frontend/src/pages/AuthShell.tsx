import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import styles from './AuthPages.module.css';
import { IconChart, IconReceipt, IconShield } from '@/components/ui/Icons';

interface AuthShellProps {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer: ReactNode;
}

/**
 * Shared frame for the login and register pages.
 *
 * The claims in the brand panel are deliberately ones the implementation
 * actually supports -- role-based access, ownership checks, database-computed
 * analytics. No "bank-level security" or invented certifications: a portfolio
 * project that advertises guarantees it cannot demonstrate undermines the very
 * thing it is meant to show.
 */
export function AuthShell({ title, subtitle, children, footer }: AuthShellProps) {
  return (
    <div className={styles.page}>
      <div className={styles.formSide}>
        <div className={styles.formInner}>
          <Link to="/" className={styles.brandRow}>
            <span className={styles.brandMark} aria-hidden="true">
              <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M3 17l6-6 4 4 8-8" />
                <path d="M17 7h4v4" />
              </svg>
            </span>
            <span className={styles.brandName}>FinTrack</span>
          </Link>

          <div className={styles.heading}>
            <h1 className={styles.title}>{title}</h1>
            <p className={styles.subtitle}>{subtitle}</p>
          </div>

          {children}

          <p className={styles.footer}>{footer}</p>
        </div>
      </div>

      {/* Decorative panel: hidden from assistive technology because it repeats the
          feature list already on the landing page and adds nothing to the task of
          signing in. */}
      <div className={styles.brandSide} aria-hidden="true">
        <div className={styles.brandContent}>
          <h2 className={styles.brandHeadline}>Know where your money actually goes.</h2>
          <p className={styles.brandCopy}>
            FinTrack records income and expenses, categorises them, and turns them into
            totals you can trust — every figure computed from your own ledger.
          </p>

          <ul className={styles.brandList}>
            <li className={styles.brandItem}>
              <span className={styles.brandItemIcon}>
                <IconReceipt size={16} />
              </span>
              <span className={styles.brandItemText}>
                <span className={styles.brandItemTitle}>Complete transaction history</span>
                <span className={styles.brandItemDesc}>
                  Filter by type, category, date range or amount.
                </span>
              </span>
            </li>
            <li className={styles.brandItem}>
              <span className={styles.brandItemIcon}>
                <IconChart size={16} />
              </span>
              <span className={styles.brandItemText}>
                <span className={styles.brandItemTitle}>Analytics from real data</span>
                <span className={styles.brandItemDesc}>
                  Category breakdowns and monthly trends, aggregated in the database.
                </span>
              </span>
            </li>
            <li className={styles.brandItem}>
              <span className={styles.brandItemIcon}>
                <IconShield size={16} />
              </span>
              <span className={styles.brandItemText}>
                <span className={styles.brandItemTitle}>Records only you can reach</span>
                <span className={styles.brandItemDesc}>
                  Ownership is verified server-side on every single request.
                </span>
              </span>
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
}
