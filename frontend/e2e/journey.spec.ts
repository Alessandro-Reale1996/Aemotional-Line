import { type Browser, devices, type Page } from '@playwright/test';

import { BASE_URL } from './environment';
import { expect, test } from './fixtures';
import { activateAndLogIn, administrator, freshEmail, freshPassword, logIn } from './helpers';

/**
 * The whole path on a phone (CLAUDE.md, F11), on the jar, through the interface only, with no shortcut through the API:
 * the administrator creates the therapist, who invites a couple; both partners activate and accept; the agreement is
 * stipulated with a counter-proposal; a question and its pointed answer; a specific discussion expanding a paragraph,
 * closed by agreement with the outcomes; a reply delay proposed and approved; a search in the archive. Three people,
 * each in a phone-sized context of their own (Alex is the test's own page, which the common checks watch); at every page
 * nothing may make it scroll sideways, a long word without spaces included. Run only by the "phone" project.
 */
const LONG = 'Supercalifragilistichespiralidoso'.repeat(3);
/** A discussion's title is shown outside any card, as the page's heading: the long word there too. */
const TITLE = `Il mare ${'Supercalifragilistichespiralidoso'.repeat(2)}`;

/** A browser of its own for another person, sized and behaving like a phone. */
async function phone(browser: Browser): Promise<Page>
{
  const context = await browser.newContext({ ...devices['Pixel 7'], baseURL: BASE_URL, locale: 'it-IT', timezoneId: 'Europe/Rome' });

  return context.newPage();
}

/** Nothing on the page makes it scroll sideways. */
async function fits(page: Page)
{
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBe(0);
}

/** A link of the bar, through the menu a phone shows instead of the links. */
async function bar(page: Page, link: string)
{
  await page.getByRole('button', { name: 'Menu' }).click();
  await page.getByRole('link', { name: link, exact: true }).click();
}

/** Clicks a button, then the button that confirms it in the dialog that asks first. */
async function confirm(page: Page, button: string, inDialog = button)
{
  await page.getByRole('button', { name: button }).click();
  await page.getByRole('dialog').getByRole('button', { name: inDialog }).click();
}

async function fillCard(page: Page, name: string, title: string | undefined, text: string)
{
  const card = page.getByRole('region', { name });

  if (title !== undefined)
  {
    await card.getByLabel('Titolo', { exact: true }).fill(title);
  }

  await card.getByLabel('Testo', { exact: true }).fill(text);
}

async function reviewAndSend(page: Page)
{
  await page.getByRole('button', { name: 'Rivedi e invia' }).click();
  await fits(page);
  await page.getByRole('dialog', { name: 'Rivedi il messaggio' }).getByRole('button', { name: 'Invia', exact: true }).click();
  await expect(page.getByText(/^Hai inviato l’ultimo messaggio: ora tocca/)).toBeVisible();
  await fits(page);
}

async function declare(page: Page, outcome: 'Risolto' | 'Arenato', reason: string)
{
  await page.getByLabel(outcome).check();
  await page.getByLabel('Motivo').fill(reason);
  await confirm(page, 'Dichiara l’esito', 'Dichiara');
  await expect(page.getByText(new RegExp(`^Il tuo esito: ${outcome} · `))).toBeVisible();
  await fits(page);
}

test('the whole path, on a phone', async ({ page, browser }) =>
{
  test.setTimeout(180_000);
  const therapist = { email: freshEmail('terapeuta'), password: freshPassword(), page: await phone(browser) };
  const alex = { email: freshEmail('alex'), password: freshPassword(), page };
  const sam = { email: freshEmail('sam'), password: freshPassword(), page: await phone(browser) };

  // The administrator creates the therapist, who activates the account from the email.
  const admin = await phone(browser);
  await logIn(admin, administrator().email, administrator().password);
  await expect(admin).toHaveURL(/\/amministrazione$/);
  await admin.getByLabel('Nome').fill('Dott.ssa Rossi');
  await admin.getByLabel('Email').fill(therapist.email);
  await admin.getByRole('button', { name: 'Crea l’account' }).click();
  await expect(admin.getByText(`${therapist.email} · In attesa di attivazione`)).toBeVisible();
  await fits(admin);
  await admin.context().close();
  await activateAndLogIn(therapist.page, therapist.email, therapist.password);
  await expect(therapist.page.getByRole('heading', { name: 'Coppie' })).toBeVisible();
  await fits(therapist.page);

  // The therapist invites the couple; both partners activate their accounts and accept.
  await bar(therapist.page, 'Invita');
  await therapist.page.getByLabel('Nome').nth(0).fill('Alex');
  await therapist.page.getByLabel('Email').nth(0).fill(alex.email);
  await therapist.page.getByLabel('Nome').nth(1).fill('Sam');
  await therapist.page.getByLabel('Email').nth(1).fill(sam.email);
  await fits(therapist.page);
  await therapist.page.getByRole('button', { name: 'Invia l’invito' }).click();
  await expect(therapist.page.getByText('Invito inviato.')).toBeVisible();

  for (const partner of [alex, sam])
  {
    await activateAndLogIn(partner.page, partner.email, partner.password);
    await fits(partner.page);
    await partner.page.getByRole('button', { name: 'Accetta' }).click();
  }

  await expect(sam.page.getByRole('heading', { name: 'Percorso in corso' })).toBeVisible();
  await fits(sam.page);

  // The agreement, stipulated with a counter-proposal.
  await alex.page.reload();
  await alex.page.getByRole('link', { name: 'Alex e Sam' }).click();
  await alex.page.getByRole('link', { name: 'Proponi l’accordo' }).click();
  await alex.page.getByLabel('Testo dell’accordo').fill(`Ci scriviamo con calma. ${LONG}`);
  await alex.page.getByRole('button', { name: 'Invia la richiesta' }).click();
  await expect(alex.page.getByText('In attesa della risposta di Sam.')).toBeVisible();
  await fits(alex.page);

  await sam.page.goto('/');
  await sam.page.getByRole('link', { name: 'Stipula dell’accordo: tocca a te.' }).click();
  await sam.page.getByRole('button', { name: 'Accetta la richiesta' }).click();
  await expect(sam.page.getByText('Tocca ad Alex.')).toBeVisible();

  await alex.page.reload();
  await alex.page.getByLabel('Motivazione').fill('Così abbiamo il tempo di pensare.');
  await alex.page.getByRole('button', { name: 'Invia la proposta' }).click();
  await expect(alex.page.getByText('Tocca a Sam.')).toBeVisible();

  await sam.page.reload();
  await sam.page.getByRole('button', { name: 'Rifiuta e proponi una modifica' }).click();
  await sam.page.getByLabel('Testo dell’accordo').fill(`Ci scriviamo con calma, ogni settimana. ${LONG}`);
  await sam.page.getByLabel('Motivazione').fill('Ho bisogno di sapere che ci sentiamo.');
  await fits(sam.page);
  await sam.page.getByRole('button', { name: 'Invia la proposta' }).click();
  await expect(sam.page.getByText('Tocca ad Alex.')).toBeVisible();

  await alex.page.reload();
  await fits(alex.page);
  await confirm(alex.page, 'Accetta la proposta');
  await expect(alex.page.getByText('Accordo stipulato: la conversazione è iniziata.')).toBeVisible();
  await fits(alex.page);

  // A question, opened and answered with a pointed answer.
  await alex.page.getByRole('link', { name: 'Discorso principale' }).click();
  await alex.page.getByRole('button', { name: 'Scrivi il primo messaggio' }).click();
  await alex.page.getByRole('button', { name: 'Aggiungi una domanda' }).click();
  await fillCard(alex.page, 'Domanda 1', 'Dove andiamo in vacanza?', `Al mare o in montagna? ${LONG}`);
  await fits(alex.page);
  await reviewAndSend(alex.page);

  await sam.page.goto('/');
  await sam.page.getByRole('link', { name: 'Discorso principale: 1 messaggio da aprire.' }).click();
  await sam.page.getByRole('button', { name: 'Apri' }).click();
  await sam.page.getByRole('region', { name: 'Dove andiamo in vacanza?' }).getByRole('button', { name: 'Rispondi' }).click();
  await fillCard(sam.page, 'Risposta puntuale 1', undefined, 'Al mare, come sempre.');
  await fits(sam.page);
  await reviewAndSend(sam.page);

  // Alex opens the answer and asks to expand it in a discussion of its own.
  await bar(alex.page, 'Conversazione');
  await alex.page.getByRole('link', { name: 'Discorso principale' }).click();
  await alex.page.getByRole('button', { name: 'Apri' }).click();
  await alex.page.getByRole('region', { name: 'In risposta a «Dove andiamo in vacanza?»' }).getByRole('link', { name: 'Approfondisci' }).click();
  await alex.page.getByLabel('Titolo', { exact: true }).fill(TITLE);
  await alex.page.getByLabel('Sottotitolo').fill('Quale e quando');
  await alex.page.getByRole('button', { name: 'Invia la richiesta' }).click();
  await expect(alex.page.getByText('In attesa della risposta di Sam.')).toBeVisible();

  await sam.page.goto('/');
  await sam.page.getByRole('link', { name: 'Richiesta di un discorso specifico: tocca a te.' }).click();
  await sam.page.getByRole('button', { name: 'Accetta la richiesta' }).click();
  await alex.page.reload();
  await alex.page.getByLabel('Motivazione').fill('Ne parliamo a parte.');
  await alex.page.getByRole('button', { name: 'Invia la proposta' }).click();
  await sam.page.reload();
  await confirm(sam.page, 'Accetta la proposta');
  await expect(sam.page.getByText('Il discorso specifico è aperto.')).toBeVisible();
  await expect(sam.page.getByRole('heading', { name: TITLE, level: 1 })).toBeVisible();
  await fits(sam.page);

  // Closed by agreement; the outcomes, each hidden from the other until both have declared.
  await sam.page.getByRole('button', { name: 'Chiedi di chiudere il discorso' }).click();
  await alex.page.goto('/');
  await alex.page.getByRole('link', { name: 'Discorso 1: una richiesta di chiusura aspetta la tua risposta.' }).click();
  await confirm(alex.page, 'Accetta e chiudi', 'Chiudi il discorso');
  await expect(alex.page.getByText(/^Il discorso è stato chiuso il .+ di comune accordo\.$/)).toBeVisible();
  await declare(alex.page, 'Risolto', 'Abbiamo scelto insieme.');
  await sam.page.goto('/');
  await sam.page.getByRole('link', { name: 'Discorso 1: dichiara l’esito.' }).click();
  await expect(sam.page.getByText('Alex ha dichiarato il suo esito: lo vedrai quando avrai dichiarato anche tu.')).toBeVisible();
  await declare(sam.page, 'Arenato', 'Io volevo la montagna.');
  await expect(sam.page.getByText(/^Esito di Alex: Risolto · /)).toBeVisible();

  // The therapist proposes a reply delay; both partners approve it from their "Da fare".
  await therapist.page.goto('/');
  await therapist.page.getByRole('link', { name: 'Alex e Sam' }).click();
  await therapist.page.getByRole('link', { name: 'Apri la conversazione' }).click();
  await therapist.page.getByRole('link', { name: 'Vincoli' }).click();
  await therapist.page.getByRole('button', { name: 'Proponi nuovi vincoli' }).click();
  await therapist.page.getByRole('button', { name: 'Aggiungi un vincolo' }).click();
  const delay = therapist.page.getByRole('region', { name: 'Vincolo 1' });
  await delay.getByLabel('Per', { exact: true }).selectOption({ label: 'Alex' });
  await delay.getByLabel('Tipo').selectOption({ label: 'Attesa prima di rispondere' });
  await delay.getByLabel('Durata').fill('1');
  await delay.getByLabel('Unità').selectOption({ label: 'ore' });
  await fits(therapist.page);
  await therapist.page.getByRole('button', { name: 'Rivedi e proponi' }).click();
  await therapist.page.getByRole('dialog', { name: 'Proporre questi vincoli?' }).getByRole('button', { name: 'Proponi' }).click();
  await expect(therapist.page.getByText('Nessuno l’ha ancora approvata.')).toBeVisible();
  await fits(therapist.page);

  for (const partner of [alex, sam])
  {
    await partner.page.goto('/');
    await partner.page.getByRole('link', { name: 'Una proposta di vincoli aspetta la tua risposta.' }).click();
    await confirm(partner.page, 'Approva');
    await fits(partner.page);
  }

  await expect(sam.page.getByText(/^Approvata da entrambi il /)).toBeVisible();
  await expect(sam.page.getByText('Alex deve attendere 1 ora dall’apertura di un messaggio prima di rispondere.').first()).toBeVisible();

  // The archive: a word found and marked without false spaces, and the history of its topic.
  await bar(alex.page, 'Archivio');
  await alex.page.getByLabel('Cerca una parola o una frase').fill('mare');
  await alex.page.getByRole('button', { name: 'Cerca' }).click();
  await expect(alex.page.getByRole('heading', { name: /^Nei messaggi \(\d+\)$/ })).toBeVisible();
  await expect(alex.page.locator('mark').first()).toHaveCSS('padding-left', '0px');
  await fits(alex.page);
  await alex.page.getByRole('link', { name: 'Storia dell’argomento' }).first().click();
  await expect(alex.page.getByRole('heading', { name: 'Storia dell’argomento' })).toBeVisible();
  await fits(alex.page);

  await Promise.all([therapist.page.context().close(), sam.page.context().close()]);
});
