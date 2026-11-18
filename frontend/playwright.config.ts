import { defineConfig, devices } from '@playwright/test';

import { BASE_URL } from './e2e/environment';

/**
 * End-to-end tests on the real jar (CLAUDE.md, frontend point 9): npm run e2e builds it, then runs them; npx playwright
 * test reuses the last jar. One worker, since the tests share one database. No traces: they would record what the tests
 * type, the generated passwords included (turn them on locally with --trace on when needed).
 */
export default defineConfig({
  testDir: 'e2e',
  globalSetup: './e2e/global-setup.ts',
  workers: 1,
  fullyParallel: false,
  forbidOnly: true,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: BASE_URL,
    locale: 'it-IT',
    timezoneId: 'Europe/Rome',
    trace: 'off',
    screenshot: 'only-on-failure',
  },
  // The whole path on a phone (F11) runs on its own project; every other test on the two desktop browsers.
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] }, testIgnore: /journey\.spec\.ts/ },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] }, testIgnore: /journey\.spec\.ts/ },
    { name: 'phone', use: { ...devices['Pixel 7'] }, testMatch: /journey\.spec\.ts/ },
  ],
});
