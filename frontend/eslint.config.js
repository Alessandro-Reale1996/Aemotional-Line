import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import { defineConfig } from 'eslint/config';
import globals from 'globals';
import tseslint from 'typescript-eslint';

// What the browser must never keep (CLAUDE.md, frontend point 7): no therapy content outlives the page.
const storage = [
  { name: 'localStorage', message: 'Nothing is saved in the browser (frontend point 7).' },
  { name: 'sessionStorage', message: 'Nothing is saved in the browser (frontend point 7).' },
  { name: 'indexedDB', message: 'Nothing is saved in the browser (frontend point 7).' },
];
const storageOnWindow = ['window', 'globalThis', 'self'].flatMap((object) =>
  ['localStorage', 'sessionStorage', 'indexedDB'].map((property) => ({
    object,
    property,
    message: 'Nothing is saved in the browser (frontend point 7).',
  })),
);
// Users' text is shown as plain text, never as HTML (frontend point 8).
const noHtml = {
  selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
  message: "Users' text is shown as plain text, never as HTML (frontend point 8).",
};

export default defineConfig(
  { ignores: ['dist', 'node', 'node_modules', 'src/api/schema.d.ts', 'playwright-report', 'test-results'] },
  js.configs.recommended,
  tseslint.configs.strict,
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: { globals: globals.browser },
    extends: [reactHooks.configs.flat.recommended],
    rules: {
      'no-restricted-syntax': ['error', noHtml],
      'no-restricted-properties': ['error', ...storageOnWindow],
      // No content in the console; errors only, and only in development (frontend points 4 and 7).
      'no-console': ['error', { allow: ['error'] }],
      // Only src/api/ talks to the server (frontend point 2).
      'no-restricted-globals': [
        'error',
        ...storage,
        { name: 'fetch', message: 'Only src/api/ talks to the server (frontend point 2).' },
        { name: 'XMLHttpRequest', message: 'Only src/api/ talks to the server (frontend point 2).' },
      ],
    },
  },
  {
    files: ['src/api/**/*.ts'],
    rules: { 'no-restricted-globals': ['error', ...storage] },
  },
  {
    files: ['*.config.{ts,js}', 'e2e/**/*.ts'],
    languageOptions: { globals: globals.node },
  },
);
