import type { Page } from '@playwright/test';

import { expect, test } from './fixtures';
import { openDiscussionThroughTheApi, setUpCouple, startConversation } from './helpers';

/**
 * Closing a specific discussion (CLAUDE.md, F9), on the jar, through the interface: by agreement, with the outcomes hidden
 * until both partners have declared; through the therapist after a failed attempt, with a justification the other partner
 * never reads. The conversation and the discussion are set up through the API.
 */

/** Clicks a button, then the button of the same name in the dialog that asks first. */
async function confirm(page: Page, button: string, inDialog = button)
{
  await page.getByRole('button', { name: button }).click();
  await page.getByRole('dialog').getByRole('button', { name: inDialog }).click();
}

async function declare(page: Page, outcome: 'Risolto' | 'Arenato', reason: string)
{
  await page.getByLabel(outcome).check();
  await page.getByLabel('Motivo').fill(reason);
  await confirm(page, 'Dichiara l’esito', 'Dichiara');
}

test('a discussion closed by agreement, the outcomes hidden until both have declared', async ({ page, browser }) =>
{
  const { therapist, alex, sam, couple, close } = await setUpCouple(browser, page);
  const conversation = await startConversation(alex, sam, couple);
  const number = await openDiscussionThroughTheApi(alex, sam, conversation, 'Le vacanze', 'Dove andare, e con chi');
  const address = `/conversazioni/${conversation}/discorsi/${number}`;

  // The main discussion has nothing to close.
  await alex.page.goto(`/conversazioni/${conversation}/discorsi/0`);
  await expect(alex.page.getByText('Puoi scrivere il primo messaggio.')).toBeVisible();
  await expect(alex.page.getByRole('heading', { name: 'Chiusura del discorso' })).toHaveCount(0);

  await alex.page.goto(address);
  await expect(alex.page.getByText('Per chiudere il discorso serve l’accordo di entrambi.')).toBeVisible();
  await alex.page.getByRole('button', { name: 'Chiedi di chiudere il discorso' }).click();
  await expect(alex.page.getByText(/^Hai chiesto a Sam di chiudere il discorso il .+\. In attesa della sua risposta\.$/)).toBeVisible();

  // Sam finds it among the things to do, and accepts.
  await sam.page.goto('/');
  await sam.page.getByRole('link', { name: `Discorso ${number}: una richiesta di chiusura aspetta la tua risposta.` }).click();
  await expect(sam.page.getByText(/^Alex chiede di chiudere il discorso/)).toBeVisible();
  await confirm(sam.page, 'Accetta e chiudi', 'Chiudi il discorso');
  await expect(sam.page.getByText(/^Il discorso è stato chiuso il .+ di comune accordo\.$/)).toBeVisible();
  await expect(sam.page.getByText('Questo discorso è chiuso: si può solo consultare.')).toBeVisible();
  await expect(sam.page.getByRole('button', { name: 'Scrivi il primo messaggio' })).toHaveCount(0);
  await declare(sam.page, 'Risolto', 'Abbiamo scelto il mare.');
  await expect(sam.page.getByText(/^Il tuo esito: Risolto · /)).toBeVisible();
  await expect(sam.page.getByText('Alex non ha ancora dichiarato l’esito.')).toBeVisible();

  // Alex doesn't see Sam's outcome before declaring their own.
  await alex.page.reload();
  await expect(alex.page.getByText('Sam ha dichiarato il suo esito: lo vedrai quando avrai dichiarato anche tu.')).toBeVisible();
  await expect(alex.page.getByText('Abbiamo scelto il mare.')).toHaveCount(0);
  await declare(alex.page, 'Arenato', 'Io avrei preferito la montagna.');
  await expect(alex.page.getByText(/^Il tuo esito: Arenato · /)).toBeVisible();
  await expect(alex.page.getByText(/^Esito di Sam: Risolto · /)).toBeVisible();
  await expect(alex.page.getByText('Abbiamo scelto il mare.')).toBeVisible();
  await expect(alex.page.getByRole('button', { name: 'Dichiara l’esito' })).toHaveCount(0);

  await therapist.page.goto(address);
  await expect(therapist.page.getByText(/^Esito di Alex: Arenato · /)).toBeVisible();
  await expect(therapist.page.getByText(/^Esito di Sam: Risolto · /)).toBeVisible();
  await expect(therapist.page.getByText(/^Richiesta di Alex · inviata il .+ · Accettata il /)).toBeVisible();
  await close();
});

test('a discussion closed by the therapist after a failed attempt, the justification hidden from the other partner', async ({ page, browser }) =>
{
  const { therapist, alex, sam, couple, close } = await setUpCouple(browser, page);
  const conversation = await startConversation(alex, sam, couple);
  const number = await openDiscussionThroughTheApi(alex, sam, conversation, 'Le vacanze', 'Dove andare, e con chi');
  const address = `/conversazioni/${conversation}/discorsi/${number}`;
  const justification = 'Ne abbiamo parlato per settimane senza muoverci.';

  // A request withdrawn is a failed attempt: then the therapist can be asked.
  await alex.page.goto(address);
  await expect(alex.page.getByRole('button', { name: 'Chiedi a Dott.ssa Rossi' })).toHaveCount(0);
  await alex.page.getByRole('button', { name: 'Chiedi di chiudere il discorso' }).click();
  await alex.page.getByRole('button', { name: 'Ritira la richiesta' }).click();
  await expect(alex.page.getByText('Hai già provato a chiuderlo con Sam senza riuscirci: puoi chiedere a Dott.ssa Rossi di chiuderlo.')).toBeVisible();
  await expect(alex.page.getByText(/^La tua richiesta · inviata il .+ · Ritirata il /)).toBeVisible();
  await alex.page.getByRole('button', { name: 'Chiedi a Dott.ssa Rossi' }).click();
  await alex.page.getByLabel('Motivazione').fill(justification);
  await alex.page.getByRole('button', { name: 'Invia la richiesta' }).click();
  await expect(alex.page.getByText(/^Hai chiesto a Dott\.ssa Rossi di chiudere il discorso il .+\. In attesa della sua decisione\.$/)).toBeVisible();
  await expect(alex.page.getByText(justification)).toBeVisible();

  // Sam knows of the request, never its justification.
  await sam.page.goto(address);
  await expect(sam.page.getByText(/^Alex ha chiesto a Dott\.ssa Rossi di chiudere il discorso il /)).toBeVisible();
  await expect(sam.page.getByText(justification)).toHaveCount(0);
  await expect(sam.page.getByRole('button', { name: 'Chiedi di chiudere il discorso' })).toHaveCount(0);

  // The therapist decides from the "Da fare", seeing the earlier request with its times.
  await therapist.page.goto('/');
  await therapist.page.getByRole('link', { name: `Discorso ${number}: una richiesta di chiusura aspetta la tua decisione.` }).click();
  await expect(therapist.page.getByText(/^Alex ti chiede di chiudere il discorso/)).toBeVisible();
  await expect(therapist.page.getByText(justification)).toBeVisible();
  await expect(therapist.page.getByRole('listitem').filter({ hasText: /^Richiesta di Alex · inviata il .+, \d\d:\d\d · Ritirata il .+, \d\d:\d\d$/ })).toHaveCount(2);
  await confirm(therapist.page, 'Chiudi il discorso');
  await expect(therapist.page.getByText(/^Il discorso è stato chiuso il .+ da te, su richiesta di Alex\.$/)).toBeVisible();

  await alex.page.reload();
  await expect(alex.page.getByText(/^Il discorso è stato chiuso il .+ da Dott\.ssa Rossi, su tua richiesta\.$/)).toBeVisible();
  await expect(alex.page.getByRole('button', { name: 'Dichiara l’esito' })).toBeVisible();
  await close();
});
