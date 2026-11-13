import type { Browser, Page } from '@playwright/test';

import { expect, test } from './fixtures';
import { activateAndLogIn, administrator, anotherPerson, emailedLink, freshEmail, freshPassword, logIn } from './helpers';

/**
 * Accounts and couples (CLAUDE.md, F5), on the jar, through the interface only: the administrator and the therapists, a
 * couple from the invitation to the deletion of its data, the account a patient deletes, the activation links renewed.
 */
async function confirmWithPassword(page: Page, button: string, password: string)
{
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('La tua password', { exact: true }).fill(password);
  await dialog.getByRole('button', { name: button }).click();
  await expect(dialog).toBeHidden();
}

/** The administrator creates a therapist from the administration page; the therapist activates the account and logs in. */
async function newTherapist(browser: Browser, therapistPage: Page, name: string)
{
  const email = freshEmail('terapeuta');
  const password = freshPassword();
  const page = await anotherPerson(browser);
  await logIn(page, administrator().email, administrator().password);
  await expect(page).toHaveURL(/\/amministrazione$/);
  await page.getByLabel('Nome').fill(name);
  await page.getByLabel('Email').fill(email);
  await page.getByRole('button', { name: 'Crea l’account' }).click();
  await expect(page.getByText(`${email} · In attesa di attivazione`)).toBeVisible();
  await page.context().close();
  await activateAndLogIn(therapistPage, email, password);
  await expect(therapistPage.getByRole('heading', { name: 'Coppie' })).toBeVisible();

  return { email, password };
}

test('a couple, from the invitation to the deletion of its data', async ({ page, browser, context }) =>
{
  const therapistName = 'Dott.ssa Rossi';
  const therapist = await newTherapist(browser, page, therapistName);
  const alex = { email: freshEmail('alex'), password: freshPassword() };
  const sam = { email: freshEmail('sam'), password: freshPassword() };

  // The therapist writes the invitation; the session expires before it is sent, and the text stays behind the login.
  await page.getByRole('link', { name: 'Invita una coppia' }).click();
  const names = page.getByLabel('Nome');
  const emails = page.getByLabel('Email');
  await names.nth(0).fill('Alex');
  await emails.nth(0).fill(alex.email);
  await names.nth(1).fill('Sam');
  await emails.nth(1).fill(sam.email);
  await context.clearCookies({ name: '__Host-SESSION' });
  await page.getByRole('button', { name: 'Invia l’invito' }).click();
  const login = page.getByRole('dialog');
  await expect(login.getByText(/il testo che stavi scrivendo è ancora qui/)).toBeVisible();
  await expect(login.getByLabel('Email')).toHaveValue(therapist.email);
  await login.getByLabel('Password', { exact: true }).fill(therapist.password);
  await login.getByRole('button', { name: 'Accedi' }).click();
  await expect(login).toBeHidden();
  await expect(page.getByLabel('Email').nth(1)).toHaveValue(sam.email);
  await page.getByRole('button', { name: 'Invia l’invito' }).click();
  await expect(page.getByText('Invito inviato.')).toBeVisible();
  await expect(page.getByText('Nessuno ha ancora accettato')).toBeVisible();

  // Both activate their accounts from the emails, find the invitation at home and accept it.
  const alexPage = await anotherPerson(browser);
  await activateAndLogIn(alexPage, alex.email, alex.password);
  await expect(alexPage.getByText(`${therapistName} ti invita a iniziare un percorso di coppia con Sam.`, { exact: false })).toBeVisible();
  await alexPage.getByRole('button', { name: 'Accetta' }).click();
  await expect(alexPage.getByText('Hai accettato: manca la risposta di Sam.')).toBeVisible();

  const samPage = await anotherPerson(browser);
  await activateAndLogIn(samPage, sam.email, sam.password);
  await samPage.getByRole('button', { name: 'Accetta' }).click();
  await expect(samPage.getByRole('heading', { name: 'Percorso in corso' })).toBeVisible();
  await expect(samPage.getByText('Per ora non c’è niente che aspetti una tua risposta.')).toBeVisible();

  // The therapist ends the couple, then deletes its data, each time with the password.
  await page.goto('/');
  await page.getByRole('link', { name: 'Alex e Sam' }).click();
  await page.getByRole('button', { name: 'Concludi il percorso' }).click();
  await confirmWithPassword(page, 'Concludi il percorso', therapist.password);
  await expect(page.getByText(/^Percorso concluso il /)).toBeVisible();
  await page.getByRole('button', { name: 'Elimina i dati' }).click();
  await confirmWithPassword(page, 'Elimina i dati', therapist.password);
  await expect(page.getByText('I dati della coppia sono stati eliminati.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Alex e Sam' })).toHaveCount(0);

  // Sam, no longer in an active couple, deletes the account.
  await samPage.goto('/account');
  await samPage.getByRole('button', { name: 'Elimina l’account' }).click();
  await confirmWithPassword(samPage, 'Elimina l’account', sam.password);
  await expect(samPage.getByText('L’account è stato eliminato.')).toBeVisible();
  await logIn(samPage, sam.email, sam.password);
  await expect(samPage.getByText(/^Email o password non validi\./)).toBeVisible();

  for (const other of [alexPage, samPage])
  {
    await other.context().close();
  }
});

test('the administrator sends a new link, then disables a therapist', async ({ page, browser }) =>
{
  const email = freshEmail('terapeuta');
  await logIn(page, administrator().email, administrator().password);
  await page.getByLabel('Nome').fill('Dott. Neri');
  await page.getByLabel('Email').fill(email);
  await page.getByRole('button', { name: 'Crea l’account' }).click();
  const first = await emailedLink(email, '/attiva');

  const card = page.locator('.card', { hasText: email });
  await card.getByRole('button', { name: 'Invia di nuovo il link' }).click();
  await expect(card.getByText('Link inviato.')).toBeVisible();
  await expect.poll(() => emailedLink(email, '/attiva')).not.toBe(first);

  const therapistPage = await anotherPerson(browser);
  await therapistPage.goto(first);
  await therapistPage.getByLabel('Password', { exact: true }).fill(freshPassword());
  await therapistPage.getByRole('button', { name: 'Attiva l’account' }).click();
  await expect(therapistPage.getByText(/^Il link non è valido/)).toBeVisible();
  const password = freshPassword();
  await activateAndLogIn(therapistPage, email, password);
  await expect(therapistPage.getByRole('heading', { name: 'Coppie' })).toBeVisible();

  await page.reload();
  await card.getByRole('button', { name: 'Disattiva' }).click();
  await expect(page.getByRole('dialog').getByText(/^Disattivando l’account di «Dott. Neri»/)).toBeVisible();
  await confirmWithPassword(page, 'Disattiva', administrator().password);
  await expect(card.getByText(`${email} · Disattivato`)).toBeVisible();
  await card.getByRole('link', { name: 'Percorsi conclusi' }).click();
  await expect(page.getByText('Nessun percorso concluso.')).toBeVisible();

  // The therapist's session ended with the disabling: the next page goes to the login.
  await therapistPage.reload();
  await expect(therapistPage).toHaveURL(/\/accedi$/);
  await therapistPage.context().close();
});

test('a person invited again before activating the account gets a new link', async ({ page, browser }) =>
{
  await newTherapist(browser, page, 'Dott.ssa Bianchi');
  const alex = freshEmail('alex');

  for (const other of ['sam', 'robin'])
  {
    await page.goto('/inviti/nuovo');
    await page.getByLabel('Nome').nth(0).fill('Alex');
    await page.getByLabel('Email').nth(0).fill(alex);
    await page.getByLabel('Nome').nth(1).fill(other);
    await page.getByLabel('Email').nth(1).fill(freshEmail(other));
    await page.getByRole('button', { name: 'Invia l’invito' }).click();
    await expect(page.getByText('Invito inviato.')).toBeVisible();
  }

  const alexPage = await anotherPerson(browser);
  await activateAndLogIn(alexPage, alex, freshPassword());
  await expect(alexPage.getByRole('button', { name: 'Accetta' })).toHaveCount(2);
  await alexPage.context().close();
});

test('a page of another role is not found', async ({ page }) =>
{
  await logIn(page, administrator().email, administrator().password);
  await expect(page).toHaveURL(/\/amministrazione$/);

  await page.goto('/inviti/nuovo');
  await expect(page.getByRole('heading', { name: 'Pagina non trovata' })).toBeVisible();
});
