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

/** A browser of its own for another person, with its own cookies. */
export async function anotherPerson(browser: Browser): Promise<Page>
{
  const context = await browser.newContext({ baseURL: BASE_URL, locale: 'it-IT', timezoneId: 'Europe/Rome' });

  return context.newPage();
}

/** Activates an account from the link emailed to it, through the page, then logs in with that password. */
export async function activateAndLogIn(page: Page, email: string, password: string)
{
  await page.goto(await emailedLink(email, '/attiva'));
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Attiva l’account' }).click();
  await expect(page.getByText('L’account è attivo. Ora puoi accedere.')).toBeVisible();
  await logIn(page, email, password);
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

/** A call to the API from inside a page of the app, with its cookies and the CSRF token, as the app makes it. */
export async function call(page: Page, method: string, path: string, body?: object): Promise<{ status: number; json: unknown }>
{
  return page.evaluate(async ({ method, path, body }) =>
  {
    const token = () => /__Host-XSRF-TOKEN=([^;]+)/.exec(document.cookie)?.[1];

    if (!token())
    {
      await fetch('/api/auth/csrf');
    }

    const answer = await fetch(path, {
      method,
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token() ?? '' },
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await answer.text();

    return { status: answer.status, json: text ? JSON.parse(text) as unknown : null };
  }, { method, path, body });
}

export interface Person
{
  name: string;
  email: string;
  password: string;
  page: Page;
}

/** Activates an account through the API, with the code of the emailed link, and logs it in on its page. */
async function activateThroughTheApi(person: Omit<Person, 'page'>, page: Page): Promise<Person>
{
  const code = new URL(await emailedLink(person.email, '/attiva')).searchParams.get('codice');
  await page.goto('/accedi');
  expect((await call(page, 'POST', '/api/auth/activate', { code, password: person.password })).status).toBe(204);
  expect((await call(page, 'POST', '/api/auth/login', { email: person.email, password: person.password })).status).toBe(200);

  return { ...person, page };
}

/**
 * A couple ready to use (CLAUDE.md, F6): a therapist, Alex and Sam, each logged in on a page of their own, set up through
 * the API (the interface for all of it is tested in couples.spec.ts). Alex's page is the one given, so that the test's own
 * checks watch it.
 */
export async function setUpCouple(browser: Browser, alexPage: Page)
{
  const therapist = await activateThroughTheApi(await (async () =>
  {
    const person = { name: 'Dott.ssa Rossi', email: freshEmail('terapeuta'), password: freshPassword() };
    await createTherapist(browser, person.email, person.name);

    return person;
  })(), await anotherPerson(browser));
  const alexAccount = { name: 'Alex', email: freshEmail('alex'), password: freshPassword() };
  const samAccount = { name: 'Sam', email: freshEmail('sam'), password: freshPassword() };
  const invited = await call(therapist.page, 'POST', '/api/invitations',
    { emailOne: alexAccount.email, nameOne: alexAccount.name, emailTwo: samAccount.email, nameTwo: samAccount.name });
  expect(invited.status).toBe(201);
  const invitation = (invited.json as { id: string }).id;
  const alex = await activateThroughTheApi(alexAccount, alexPage);
  const sam = await activateThroughTheApi(samAccount, await anotherPerson(browser));

  for (const partner of [alex, sam])
  {
    expect((await call(partner.page, 'POST', `/api/invitations/${invitation}/accept`)).status).toBe(204);
  }

  const couple = ((await call(alex.page, 'GET', '/api/me/couples')).json as { id: string }[])[0]?.id ?? '';

  return { therapist, alex, sam, couple, close: () => Promise.all([therapist.page.context().close(), sam.page.context().close()]) };
}

/** The conversation of a couple, started through the API: Alex asks, Sam accepts, Alex sends the text, Sam accepts it. */
export async function startConversation(alex: Person, sam: Person, couple: string, text = 'Ci scriviamo con calma.'): Promise<string>
{
  const started = await call(alex.page, 'POST', `/api/couples/${couple}/agreement-negotiations`, { text });
  const negotiation = (started.json as { id: string }).id;
  expect((await call(sam.page, 'POST', `/api/negotiations/${negotiation}/accept-request`)).status).toBe(204);
  expect((await call(alex.page, 'POST', `/api/negotiations/${negotiation}/send`, { justification: 'Va bene così.' })).status).toBe(204);
  const accepted = await call(sam.page, 'POST', `/api/negotiations/${negotiation}/accept-proposal`);
  expect(accepted.status).toBe(200);

  return (accepted.json as { conversationId: string }).conversationId;
}

/** A message sent through the API (the writing screens are F8's): paragraphs of a type, a title and a body. */
export async function sendThroughTheApi(person: Person, conversation: string, discussion: number,
  paragraphs: { type: 'SIMPLE' | 'QUESTION'; title: string; body: string }[])
{
  const now = new Date();
  const answer = await call(person.page, 'POST', `/api/conversations/${conversation}/discussions/${discussion}/messages`, {
    paragraphs: paragraphs.map((paragraph) => ({ ...paragraph, subtitle: null, references: [], selfCitations: [], question: null })),
    localTime: `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`,
    position: null,
  });
  expect(answer.status, 'the message was sent').toBe(201);
}

/** Constraints proposed by the therapist and approved by both partners, through the API (the screens: constraints.spec.ts). */
export async function applyConstraints(therapist: Person, partners: Person[], conversation: string, constraints: object[])
{
  const proposed = await call(therapist.page, 'POST', `/api/conversations/${conversation}/constraints/requests`, { constraints });
  expect(proposed.status, 'the constraints were proposed').toBe(201);
  const request = (proposed.json as { id: string }).id;

  for (const partner of partners)
  {
    expect((await call(partner.page, 'POST', `/api/conversations/${conversation}/constraints/requests/${request}/approve`)).status).toBe(204);
  }
}

/** The id of a person on the server, read from their own session. */
export async function idOf(person: Person): Promise<string>
{
  return ((await call(person.page, 'GET', '/api/auth/me')).json as { id: string }).id;
}

/** Opens, through the API, the last message of a discussion, as its recipient. */
export async function openThroughTheApi(person: Person, conversation: string, discussion: number)
{
  const view = (await call(person.page, 'GET', `/api/conversations/${conversation}`)).json as { discussions: { id: number; messages: { id: string }[] }[] };
  const last = view.discussions.find((candidate) => candidate.id === discussion)?.messages.at(-1)?.id;
  const now = new Date();
  const opened = await call(person.page, 'POST', `/api/conversations/${conversation}/discussions/${discussion}/messages/${last}/open`, {
    localTime: `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`,
    position: null,
  });
  expect(opened.status, 'the message was opened').toBe(200);
}

/**
 * A specific discussion opened through the API, optionally from a paragraph: Alex asks, Sam accepts, Alex sends the title,
 * Sam accepts it. Its number.
 */
export async function openDiscussionThroughTheApi(alex: Person, sam: Person, conversation: string, title: string, subtitle: string,
  originParagraphId: string | null = null): Promise<number>
{
  const started = await call(alex.page, 'POST', `/api/conversations/${conversation}/discussion-negotiations`, { title, subtitle, originParagraphId });
  const negotiation = (started.json as { id: string }).id;
  expect((await call(sam.page, 'POST', `/api/negotiations/${negotiation}/accept-request`)).status).toBe(204);
  expect((await call(alex.page, 'POST', `/api/negotiations/${negotiation}/send`, { justification: 'Ne parliamo da mesi.' })).status).toBe(204);
  const accepted = await call(sam.page, 'POST', `/api/negotiations/${negotiation}/accept-proposal`);
  expect(accepted.status).toBe(200);

  return (accepted.json as { openedDiscussion: number }).openedDiscussion;
}
