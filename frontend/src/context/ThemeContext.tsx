import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

export type Theme = 'light' | 'dark';

export interface ThemeContextValue {
  theme: Theme;
  toggleTheme: () => void;
  setTheme: (theme: Theme) => void;
}

export const ThemeContext = createContext<ThemeContextValue | null>(null);

const STORAGE_KEY = 'fintrack.theme';

/**
 * Reads the theme already applied to the document.
 *
 * The inline script in index.html has set `data-theme` before React mounted --
 * that is what prevents a flash of the wrong theme on reload. Reading it back
 * here keeps React in step instead of re-deciding and briefly disagreeing.
 */
function currentTheme(): Theme {
  const applied = document.documentElement.getAttribute('data-theme');
  return applied === 'dark' ? 'dark' : 'light';
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(currentTheme);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Storage unavailable (private browsing, blocked cookies). The theme still
      // applies for this session; it simply will not be remembered.
    }
  }, [theme]);

  const setTheme = useCallback((next: Theme) => setThemeState(next), []);
  const toggleTheme = useCallback(
    () => setThemeState((previous) => (previous === 'light' ? 'dark' : 'light')),
    [],
  );

  const value = useMemo(() => ({ theme, toggleTheme, setTheme }), [theme, toggleTheme, setTheme]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
