import type { ReactNode } from 'react';
import styles from './Card.module.css';

interface CardProps {
  children: ReactNode;
  className?: string;
  /** Applies padding directly to the card, for cards with no header. */
  padded?: boolean;
}

export function Card({ children, className, padded = false }: CardProps) {
  return (
    <section className={[styles.card, padded ? styles.padded : '', className ?? ''].filter(Boolean).join(' ')}>
      {children}
    </section>
  );
}

interface CardHeaderProps {
  title: string;
  subtitle?: string;
  /** Controls aligned to the right of the title, e.g. a filter or a link. */
  actions?: ReactNode;
  /**
   * Heading level. Defaults to h2, but a card nested inside a section should
   * pass h3 so the document outline stays sequential -- screen reader users
   * navigate by heading level, and skipping one breaks that.
   */
  as?: 'h2' | 'h3';
}

export function CardHeader({ title, subtitle, actions, as: Heading = 'h2' }: CardHeaderProps) {
  return (
    <header className={styles.header}>
      <div className={styles.headerText}>
        <Heading className={styles.title}>{title}</Heading>
        {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
      </div>
      {actions}
    </header>
  );
}

export function CardBody({ children, flush = false }: { children: ReactNode; flush?: boolean }) {
  return <div className={flush ? styles.bodyFlush : styles.body}>{children}</div>;
}
