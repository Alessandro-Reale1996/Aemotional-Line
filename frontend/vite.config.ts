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
    // One bundle on purpose (CLAUDE.md, F11 decision 3): downloaded once and kept a year, and a new version never leaves
    // an open tab asking for pieces that no longer exist. It is about 530 KB (160 KB compressed): the warning comes back
    // only if it really grows.
    chunkSizeWarningLimit: 800,
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['src/test-setup.ts'],
    // Dates and times are shown in the browser's zone: the tests fix it, as the end-to-end tests do.
    env: { TZ: 'Europe/Rome' },
    restoreMocks: true,
  },
});
