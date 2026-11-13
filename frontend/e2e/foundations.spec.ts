import { expect, test } from './fixtures';
import { administrator } from './helpers';

/**
 * The foundations (CLAUDE.md, F3), on the jar: the app opens at its pages with the CSP on, and the session lives in
 * __Host- cookies over http://localhost in a real browser (frontend point 3).
 */
test('opens the app at its pages under the CSP', async ({ page }) =>
{
  const answer = await page.goto('/accedi');
  expect(answer?.headers()['content-security-policy']).toContain("default-src 'none'");
  await expect(page.getByRole('heading', { name: 'Accedi' })).toBeVisible();

  // A page of the app opened from an email's link: the code leaves the address at once.
  await page.goto('/attiva?codice=abc');
  await expect(page.getByRole('heading', { name: 'Attiva l’account' })).toBeVisible();
  await expect(page).toHaveURL(/\/attiva$/);

  await page.goto('/una/pagina/che/non/esiste');
  await expect(page.getByRole('heading', { name: 'Pagina non trovata' })).toBeVisible();
});

test('keeps the session in cookies the page cannot read, and ends it at the logout', async ({ page }) =>
{
  await page.goto('/accedi');

  const steps = await page.evaluate(async (credentials) =>
  {
    const token = () => /__Host-XSRF-TOKEN=([^;]+)/.exec(document.cookie)?.[1] ?? '';
    const send = (path: string, body?: object) =>
      fetch(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token() },
        body: body ? JSON.stringify(body) : undefined,
      });

    await fetch('/api/auth/csrf');
    const login = await send('/api/auth/login', credentials);
    const cookiesAfterLogin = document.cookie;
    const me = await fetch('/api/auth/me');
    const role = me.ok ? ((await me.json()) as { role: string }).role : null;
    const logout = await send('/api/auth/logout');
    const meAfterLogout = await fetch('/api/auth/me');

    return { login: login.status, cookiesAfterLogin, role, logout: logout.status, afterLogout: meAfterLogout.status };
  }, administrator());

  expect(steps.login).toBe(200);
  expect(steps.cookiesAfterLogin).toContain('__Host-XSRF-TOKEN=');
  expect(steps.cookiesAfterLogin, 'the session cookie is HttpOnly').not.toContain('__Host-SESSION');
  expect(steps.role).toBe('ADMINISTRATOR');
  expect(steps.logout).toBe(204);
  expect(steps.afterLogout).toBe(401);
});
