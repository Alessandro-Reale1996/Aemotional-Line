import { test as base, expect } from '@playwright/test';

/** The only cookies the app may leave: the session and the CSRF token (CLAUDE.md, frontend point 7). */
const ALLOWED_COOKIES = ['__Host-SESSION', '__Host-XSRF-TOKEN'];

/**
 * The checks every end-to-end test passes by itself (CLAUDE.md, frontend point 9): it fails on a Content-Security-Policy
 * violation, an unhandled JavaScript error or a request to another host, and if the browser keeps any data or cookie it
 * shouldn't once the test is over.
 */
export const test = base.extend({
  page: async ({ page, baseURL }, use) =>
  {
    const origin = new URL(baseURL ?? '').origin;
    const problems: string[] = [];

    await page.addInitScript(() =>
    {
      document.addEventListener('securitypolicyviolation', (event) =>
      {
        console.error(`CSP violation: ${event.violatedDirective} blocked ${event.blockedURI || 'inline'}`);
      });
    });
    page.on('console', (message) =>
    {
      if (message.type() === 'error' && message.text().startsWith('CSP violation'))
      {
        problems.push(message.text());
      }
    });
    page.on('pageerror', (error) => problems.push(`JavaScript error: ${error.message}`));
    page.on('request', (request) =>
    {
      const url = new URL(request.url());

      if (url.protocol !== 'data:' && url.origin !== origin)
      {
        problems.push(`a request to another host: ${url.origin}`);
      }
    });

    await use(page);

    if (!page.isClosed() && page.url().startsWith(origin))
    {
      const kept = await page.evaluate(async () => ({
        localStorage: localStorage.length,
        sessionStorage: sessionStorage.length,
        indexedDB: (await indexedDB.databases()).length,
      }));

      for (const [place, count] of Object.entries(kept))
      {
        if (count > 0)
        {
          problems.push(`the browser keeps data in ${place}`);
        }
      }
    }

    for (const cookie of await page.context().cookies())
    {
      if (!ALLOWED_COOKIES.includes(cookie.name))
      {
        problems.push(`a cookie the app shouldn't leave: ${cookie.name}`);
      }
    }

    expect(problems, 'the checks every test must pass').toEqual([]);
  },
});

export { expect };
