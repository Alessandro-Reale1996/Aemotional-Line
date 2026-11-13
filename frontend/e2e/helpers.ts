import { randomUUID } from 'node:crypto';

import { type Browser, expect, type Page } from '@playwright/test';

import { BASE_URL, MAILPIT_URL } from './environment';

/** The first administrator of this run (global-setup.ts). */
export function administrator()
{
  return { email: process.env.E2E_ADMIN_EMAIL ?? '', password: process.env.E2E_ADMIN_PASSWORD ?? '' };
}

/** An email nobody else uses in this run. */
export function freshEmail(name: string)
{
  return `${name}-${randomUUID().slice(0, 8)}@example.test`;
}

/** A password generated now, never written anywhere (CLAUDE.md, frontend point 9). */
export function freshPassword()
{
  return `prova ${randomUUID()}`;
}

/** Logs in through the login page, as a person would. */
export async function logIn(page: Page, email: string, password: string)
{
  await page.goto('/accedi');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Accedi' }).click();
}

/** Logs out through the menu with the user's name. */
export async function logOut(page: Page, name: string)
{
  await page.getByRole('button', { name, exact: true }).click();
  await page.getByRole('button', { name: 'Esci' }).click();
  await expect(page.getByText('Hai chiuso la sessione.')).toBeVisible();
}

/**
 * Creates a therapist as the administrator, through the API, in a browser of its own: the administration screens come
 * with F5. The backend emails the activation link to Mailpit.
 */
export async function createTherapist(browser: Browser, email: string, displayName: string)
{
  const context = await browser.newContext({ baseURL: BASE_URL });
  const page = await context.newPage();
  await page.goto('/accedi');

  const status = await page.evaluate(async ({ admin, therapist }) =>
  {
    const token = () => /__Host-XSRF-TOKEN=([^;]+)/.exec(document.cookie)?.[1] ?? '';
    const post = (path: string, body?: object) =>
      fetch(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token() },
        body: body ? JSON.stringify(body) : undefined,
      });

    await fetch('/api/auth/csrf');
    await post('/api/auth/login', admin);
    const created = await post('/api/admin/therapists', therapist);
    await post('/api/auth/logout');

    return created.status;
  }, { admin: administrator(), therapist: { email, displayName } });

  await context.close();
  expect(status, 'the therapist was created').toBe(201);
}

interface MailpitMessage
{
  ID: string;
  To: { Address: string }[];
}

/** The link of the given page in the latest email sent to `to`, waiting for it to arrive. */
export async function emailedLink(to: string, page: '/attiva' | '/reimposta-password'): Promise<string>
{
  const pattern = new RegExp(`https?://[^\\s"<]+${page}\\?codice=[A-Za-z0-9_-]+`);
  const deadline = Date.now() + 15_000;

  while (Date.now() < deadline)
  {
    const { messages } = (await (await fetch(`${MAILPIT_URL}/api/v1/messages`)).json()) as { messages: MailpitMessage[] };

    for (const message of messages.filter((candidate) => candidate.To.some((recipient) => recipient.Address === to)))
    {
      const { Text, HTML } = (await (await fetch(`${MAILPIT_URL}/api/v1/message/${message.ID}`)).json()) as { Text: string; HTML: string };
      const link = pattern.exec(`${Text}\n${HTML}`)?.[0];

      if (link)
      {
        return link;
      }
    }

    await new Promise((wait) => setTimeout(wait, 250));
  }

  throw new Error(`No email with a link to ${page} reached ${to}.`);
}
