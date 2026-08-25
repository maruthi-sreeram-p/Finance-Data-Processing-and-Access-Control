import '@testing-library/jest-dom/vitest';

/**
 * jsdom implements only part of the browser platform. The stubs below cover the
 * APIs this application touches that jsdom does not provide, so a component using
 * them can be rendered in a test at all.
 */

// Read by ThemeProvider on mount.
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  }),
});
