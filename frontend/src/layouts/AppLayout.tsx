import { useEffect, useRef, useState, type ReactElement } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import styles from './AppLayout.module.css';
import { useAuth } from '@/hooks/useAuth';
import { useTheme } from '@/hooks/useTheme';
import { Toaster } from '@/components/ui/Toaster';
import { initialsOf } from '@/utils/format';
import {
  IconChart,
  IconClose,
  IconLogout,
  IconMenu,
  IconMoon,
  IconReceipt,
  IconShield,
  IconSun,
  IconTag,
  IconUser,
  IconUsers,
} from '@/components/ui/Icons';

interface NavItem {
  to: string;
  label: string;
  icon: ReactElement;
  /** Restricts the link to these roles. UX only -- the backend still enforces access. */
  roles?: Array<'ADMIN' | 'MANAGER' | 'USER'>;
}

const PRIMARY_NAV: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: <IconChart /> },
  { to: '/transactions', label: 'Transactions', icon: <IconReceipt /> },
  { to: '/categories', label: 'Categories', icon: <IconTag /> },
  { to: '/reports', label: 'Reports', icon: <IconChart /> },
];

/**
 * Elevated navigation. Each entry declares the roles that may see it.
 *
 * Hiding a link is a courtesy, not a control: every destination below is
 * independently role-guarded by the backend, so a user who types the URL gets a
 * page whose requests come back 403.
 */
const ELEVATED_NAV: NavItem[] = [
  { to: '/organization', label: 'Organization', icon: <IconShield />, roles: ['ADMIN', 'MANAGER'] },
  { to: '/admin/users', label: 'User management', icon: <IconUsers />, roles: ['ADMIN'] },
];

/** Page titles for the top bar, keyed by route prefix (longest match wins). */
const TITLES: Array<[string, string]> = [
  ['/dashboard', 'Dashboard'],
  ['/transactions/new', 'Add transaction'],
  ['/transactions', 'Transactions'],
  ['/categories', 'Categories'],
  ['/reports', 'Reports'],
  ['/profile', 'Profile'],
  ['/admin/users', 'User management'],
  ['/organization', 'Organization overview'],
];

function titleFor(pathname: string): string {
  const match = TITLES.filter(([prefix]) => pathname.startsWith(prefix)).sort(
    (a, b) => b[0].length - a[0].length,
  )[0];
  return match ? match[1] : 'FinTrack';
}

/**
 * The signed-in application shell: sidebar, top bar, and the routed page.
 *
 * On narrow screens the sidebar becomes an overlay drawer rather than collapsing
 * to icons — at 360px an icon rail plus content leaves too little room for a
 * table, and unlabelled icons are a guessing game.
 */
export function AppLayout() {
  const { user, logout, hasRole } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  // Closing the drawer on navigation is what makes it usable: without it, tapping
  // a link on a phone loads the page behind a drawer that is still covering it.
  useEffect(() => {
    setDrawerOpen(false);
    setMenuOpen(false);
  }, [location.pathname]);

  // Escape closes whichever overlay is open, and a click outside closes the menu.
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setDrawerOpen(false);
        setMenuOpen(false);
      }
    }

    function handlePointerDown(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    document.addEventListener('mousedown', handlePointerDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('mousedown', handlePointerDown);
    };
  }, []);

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  const visibleElevatedNav = ELEVATED_NAV.filter((item) => !item.roles || hasRole(...item.roles));

  return (
    <div className={styles.shell}>
      {/* Lets a keyboard user jump the navigation instead of tabbing through it
          on every page load. */}
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>

      <aside
        className={[styles.sidebar, drawerOpen ? styles.sidebarOpen : ''].filter(Boolean).join(' ')}
        // Hidden from assistive technology while off-canvas on mobile, so a screen
        // reader does not read out links the user cannot currently reach.
        aria-hidden={undefined}
      >
        <Link to="/dashboard" className={styles.brand}>
          <span className={styles.brandMark} aria-hidden="true">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 17l6-6 4 4 8-8" />
              <path d="M17 7h4v4" />
            </svg>
          </span>
          <span className={styles.brandName}>FinTrack</span>
        </Link>

        <nav className={styles.nav} aria-label="Main navigation">
          {PRIMARY_NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                [styles.navLink, isActive ? styles.navLinkActive : ''].filter(Boolean).join(' ')
              }
            >
              <span className={styles.navIcon} aria-hidden="true">
                {item.icon}
              </span>
              {item.label}
            </NavLink>
          ))}

          {visibleElevatedNav.length > 0 && (
            <>
              <p className={styles.navGroupLabel}>Administration</p>
              {visibleElevatedNav.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    [styles.navLink, isActive ? styles.navLinkActive : ''].filter(Boolean).join(' ')
                  }
                >
                  <span className={styles.navIcon} aria-hidden="true">
                    {item.icon}
                  </span>
                  {item.label}
                </NavLink>
              ))}
            </>
          )}
        </nav>

        <div className={styles.sidebarFooter}>
          <NavLink
            to="/profile"
            className={({ isActive }) =>
              [styles.navLink, isActive ? styles.navLinkActive : ''].filter(Boolean).join(' ')
            }
          >
            <span className={styles.navIcon} aria-hidden="true">
              <IconUser />
            </span>
            Profile
          </NavLink>
          <button type="button" className={styles.navLink} onClick={handleLogout}>
            <span className={styles.navIcon} aria-hidden="true">
              <IconLogout />
            </span>
            Log out
          </button>
        </div>
      </aside>

      {drawerOpen && (
        <div
          className={styles.scrimVisible}
          onClick={() => setDrawerOpen(false)}
          aria-hidden="true"
        />
      )}

      <div className={styles.main}>
        <header className={styles.topbar}>
          <button
            type="button"
            className={styles.menuButton}
            onClick={() => setDrawerOpen((open) => !open)}
            aria-label={drawerOpen ? 'Close navigation menu' : 'Open navigation menu'}
            aria-expanded={drawerOpen}
          >
            {drawerOpen ? <IconClose /> : <IconMenu />}
          </button>

          <h1 className={styles.topbarTitle}>{titleFor(location.pathname)}</h1>

          <div className={styles.topbarActions}>
            <button
              type="button"
              className={styles.menuButton}
              style={{ display: 'inline-grid' }}
              onClick={toggleTheme}
              aria-label={`Switch to ${theme === 'light' ? 'dark' : 'light'} theme`}
            >
              {theme === 'light' ? <IconMoon /> : <IconSun />}
            </button>

            <div className={styles.accountWrap} ref={menuRef}>
              <button
                type="button"
                className={styles.avatarButton}
                onClick={() => setMenuOpen((open) => !open)}
                aria-expanded={menuOpen}
                aria-haspopup="menu"
              >
                <span className={styles.avatar} aria-hidden="true">
                  {user ? initialsOf(user.name) : '?'}
                </span>
                <span className={styles.avatarName}>{user?.name}</span>
              </button>

              {menuOpen && user && (
                <div className={styles.menu} role="menu">
                  <div className={styles.menuHeader}>
                    <span className={styles.menuName}>{user.name}</span>
                    <span className={styles.menuEmail}>{user.email}</span>
                  </div>
                  <Link to="/profile" className={styles.menuItem} role="menuitem">
                    <IconUser />
                    Profile settings
                  </Link>
                  <button
                    type="button"
                    className={[styles.menuItem, styles.menuItemDanger].join(' ')}
                    onClick={handleLogout}
                    role="menuitem"
                  >
                    <IconLogout />
                    Log out
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>

        <main className={styles.content} id="main-content">
          <Outlet />
        </main>
      </div>

      <Toaster />
    </div>
  );
}
