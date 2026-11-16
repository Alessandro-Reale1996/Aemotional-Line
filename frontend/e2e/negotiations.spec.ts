import type { Page } from '@playwright/test';

import { expect, test } from './fixtures';
import { call, setUpCouple } from './helpers';

/**
 * The stipule (CLAUDE.md, F6), on the jar, through the interface: an agreement stipulated with a counter-proposal, a
 * request refused, a specific discussion opened. The couples are set up through the API.
 */
const AGREEMENT = 'Ci scriviamo con calma, una lettera alla volta.';

async function openTheStipula(page: Page)
{
  await page.goto('/');
  await page.getByRole('link', { name: /^Stipula dell’accordo: tocca a te\.|^Richiesta di un discorso specifico: tocca a te\./ }).click();
}

async function confirm(page: Page, button: string)
{
  await page.getByRole('button', { name: button }).click();
  await page.getByRole('dialog').getByRole('button', { name: button }).click();
}

test('an agreement stipulated with a counter-proposal', async ({ page, browser }) =>
{
  const { therapist, alex, sam, close } = await setUpCouple(browser, page);

  // Alex asks to stipulate the agreement, from the couple's page.
  await alex.page.goto('/');
  await alex.page.getByRole('link', { name: 'Alex e Sam' }).click();
  await alex.page.getByRole('link', { name: 'Proponi l’accordo' }).click();
  await alex.page.getByLabel('Testo dell’accordo').fill(AGREEMENT);
  await alex.page.getByRole('button', { name: 'Invia la richiesta' }).click();
  await expect(alex.page.getByText('Richiesta inviata.')).toBeVisible();
  await expect(alex.page.getByText('In attesa della risposta di Sam.')).toBeVisible();

  // The therapist reads it, and can do nothing.
  await therapist.page.goto(alex.page.url());
  await expect(therapist.page.getByText('In attesa della risposta di Sam.')).toBeVisible();
  await expect(therapist.page.getByRole('button', { name: /Accetta|Rifiuta|Ritira|Abbandona/ })).toHaveCount(0);

  // Sam finds it in the "Da fare" and accepts to discuss it.
  await openTheStipula(sam.page);
  await expect(sam.page.getByText('Alex chiede di avviare questa stipula. Vuoi discuterne?')).toBeVisible();
  await sam.page.getByRole('button', { name: 'Accetta la richiesta' }).click();
  await expect(sam.page.getByText('Tocca ad Alex.')).toBeVisible();

  // Alex reviews the text, saves it, then sends it with a justification.
  await alex.page.reload();
  await expect(alex.page.getByText('Tocca a te: rivedi il testo e invialo con una motivazione.')).toBeVisible();
  await alex.page.getByLabel('Testo dell’accordo').fill(`${AGREEMENT} Senza fretta.`);
  await alex.page.getByRole('button', { name: 'Salva la bozza' }).click();
  await expect(alex.page.getByText('Bozza salvata.')).toBeVisible();
  await alex.page.getByLabel('Motivazione').fill('Così abbiamo il tempo di pensare.');
  await alex.page.getByRole('button', { name: 'Invia la proposta' }).click();
  await expect(alex.page.getByText('Tocca a Sam.')).toBeVisible();

  // Sam refuses it and proposes a change.
  await sam.page.reload();
  await expect(sam.page.getByText('Alex ti ha inviato una proposta.')).toBeVisible();
  await sam.page.getByRole('button', { name: 'Rifiuta e proponi una modifica' }).click();
  await expect(sam.page.getByLabel('Testo dell’accordo')).toHaveValue(`${AGREEMENT} Senza fretta.`);
  await sam.page.getByLabel('Testo dell’accordo').fill(`${AGREEMENT} Senza fretta, ma ogni settimana.`);
  await sam.page.getByLabel('Motivazione').fill('Ho bisogno di sapere che ci sentiamo.');
  await sam.page.getByRole('button', { name: 'Invia la proposta' }).click();
  await expect(sam.page.getByText('Tocca ad Alex.')).toBeVisible();

  // Alex accepts: the agreement takes effect and the conversation starts.
  await alex.page.reload();
  await expect(alex.page.getByText('Sam ti ha inviato una proposta.')).toBeVisible();
  await expect(alex.page.getByText('Ho bisogno di sapere che ci sentiamo.')).toBeVisible();
  await confirm(alex.page, 'Accetta la proposta');
  await expect(alex.page.getByText('Accordo stipulato: la conversazione è iniziata.')).toBeVisible();
  await expect(alex.page.getByRole('link', { name: 'Proponi una modifica dell’accordo' })).toBeVisible();
  await expect(alex.page.getByText('Nessuna stipula in corso.')).toBeVisible();
  await close();
});

test('a refused request stays among the failed attempts', async ({ page, browser }) =>
{
  const { alex, sam, couple, close } = await setUpCouple(browser, page);

  await alex.page.goto(`/coppie/${couple}/nuovo-accordo`);
  await alex.page.getByLabel('Testo dell’accordo').fill(AGREEMENT);
  await alex.page.getByRole('button', { name: 'Invia la richiesta' }).click();
  await expect(alex.page.getByText('Richiesta inviata.')).toBeVisible();

  await openTheStipula(sam.page);
  await confirm(sam.page, 'Rifiuta la richiesta');
  await expect(sam.page.getByText('La stipula si è chiusa senza accordo.')).toBeVisible();
  await expect(sam.page.getByRole('heading', { name: 'Tentativi non riusciti' })).toBeVisible();
  await expect(sam.page.getByText(/richiesta da Alex il .* · rifiutata da te il /)).toBeVisible();

  await alex.page.goto(`/coppie/${couple}`);
  await expect(alex.page.getByText(/rifiutata da Sam il /)).toBeVisible();
  await expect(alex.page.getByRole('link', { name: 'Proponi l’accordo' }), 'a new request can be sent').toBeVisible();
  await close();
});

test('a specific discussion requested and opened', async ({ page, browser }) =>
{
  const { alex, sam, couple, close } = await setUpCouple(browser, page);

  // The conversation, started through the API.
  const started = await call(alex.page, 'POST', `/api/couples/${couple}/agreement-negotiations`, { text: AGREEMENT });
  const agreement = (started.json as { id: string }).id;
  expect((await call(sam.page, 'POST', `/api/negotiations/${agreement}/accept-request`)).status).toBe(204);
  expect((await call(alex.page, 'POST', `/api/negotiations/${agreement}/send`, { justification: 'Va bene così.' })).status).toBe(204);
  expect((await call(sam.page, 'POST', `/api/negotiations/${agreement}/accept-proposal`)).status).toBe(200);

  await alex.page.goto(`/coppie/${couple}`);
  await alex.page.getByRole('link', { name: 'Proponi un discorso specifico' }).click();
  await alex.page.getByLabel('Titolo', { exact: true }).fill('Le vacanze');
  await alex.page.getByLabel('Sottotitolo').fill('Dove andare, e con chi');
  await alex.page.getByRole('button', { name: 'Invia la richiesta' }).click();
  await expect(alex.page.getByRole('heading', { name: 'Richiesta di un discorso specifico' })).toBeVisible();

  await openTheStipula(sam.page);
  await expect(sam.page.getByText('Dove andare, e con chi')).toBeVisible();
  await sam.page.getByRole('button', { name: 'Accetta la richiesta' }).click();

  await alex.page.reload();
  await expect(alex.page.getByLabel('Titolo', { exact: true })).toHaveValue('Le vacanze');
  await alex.page.getByLabel('Motivazione').fill('Ne parliamo da mesi.');
  await alex.page.getByRole('button', { name: 'Invia la proposta' }).click();
  await expect(alex.page.getByText('Tocca a Sam.')).toBeVisible();

  await sam.page.reload();
  await confirm(sam.page, 'Accetta la proposta');
  await expect(sam.page.getByText('Il discorso specifico è aperto.')).toBeVisible();
  await close();
});
