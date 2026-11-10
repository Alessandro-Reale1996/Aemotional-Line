/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Development (CLAUDE.md, frontend points 1 and 3): the app on http://localhost:5173, /api passed to the backend, so that
// app and API share one origin as in production; the cookies go through unchanged. AEMOTIONAL_BACKEND points the proxy
// to another backend (the end-to-end one, for instance).
export default defineConfig({
  plugins: [react()],
  server: {
    host: 'localhost',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: process.env.AEMOTIONAL_BACKEND ?? 'http://localhost:8080' },
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    // No file turned into a data: address: the CSP allows data: only for Bootstrap's own icons (frontend point 8).
    assetsInlineLimit: 0,
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['src/test-setup.ts'],
    restoreMocks: true,
  },
});
