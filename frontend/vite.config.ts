// vitest/config, not vite: vite's own defineConfig has no `test` key.
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [react()],

  resolve: {
    alias: {
      // Vite does not read tsconfig `paths`, so the "@/..." alias has to be
      // declared here as well. TypeScript resolves it for type-checking; this is
      // what makes the bundler resolve it at build time.
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },

  server: {
    // Pinned rather than left to Vite's "next free port" behaviour: the backend
    // allows this exact origin through CORS, so a silent shift to 5174 would turn
    // every API call into an opaque CORS failure.
    port: 5173,
    strictPort: true,
  },

  build: {
    // Source maps ship so a production stack trace is readable. They expose the
    // frontend source, which is already delivered to the browser anyway -- no
    // secret lives in this bundle.
    sourcemap: true,
  },

  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: true,
  },
});
