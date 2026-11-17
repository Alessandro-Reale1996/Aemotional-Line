import type { Page } from '@playwright/test';

import { expect, test } from './fixtures';
import { call, idOf, openThroughTheApi, sendThroughTheApi, setUpCouple, startConversation } from './helpers';

/**
 * The constraints (CLAUDE.md, F9), on the jar, through the interface: the therapist proposes a whole set through the form,
 * both partners approve it from their "Da fare", it binds them; a place is shown only to whom it binds; proposals rejected
 * and withdrawn stay as they ended.
 */
const HOME = { latitude: 45.4642, longitude: 9.19 };

/** Clicks a button, then the button of the same name in the dialog that asks first. */
async function confirm(page: Page, button: string, inDialog = button)
{
  await page.getByRole('button', { name: button }).click();
  await page.getByRole('dialog').getByRole('button', { name: inDialog }).click();
}

test('constraints proposed through the form, approved by both, binding at once', async ({ page, browser }) =>
{
  const { therapist, alex, sam, couple, close } = await setUpCouple(browser, page);
  const conversation = await startConversation(alex, sam, couple);

  // The therapist reaches the page from the conversation and proposes two constraints.
  await therapist.page.goto(`/conversazioni/${conversation}`);
  await therapist.page.getByRole('link', { name: 'Vincoli' }).click();
  await expect(therapist.page.getByText('Nessun vincolo in vigore.')).toBeVisible();
  await therapist.page.getByRole('button', { name: 'Proponi nuovi vincoli' }).click();
  await therapist.page.getByRole('button', { name: 'Aggiungi un vincolo' }).click();
  const wait = therapist.page.getByRole('region', { name: 'Vincolo 1' });
  await wait.getByLabel('Per', { exact: true }).selectOption({ label: 'Alex' });
  await wait.getByLabel('Tipo').selectOption({ label: 'Attesa prima di rispondere' });
  await wait.getByLabel('Durata').fill('2');
  await wait.getByLabel('Unità').selectOption({ label: 'ore' });
  await therapist.page.getByRole('button', { name: 'Aggiungi un vincolo' }).click();
  const place = therapist.page.getByRole('region', { name: 'Vincolo 2' });
  await place.getByLabel('Per', { exact: true }).selectOption({ label: 'Sam' });
  await place.getByLabel('Tipo').selectOption({ label: 'Luogo' });
  await place.getByLabel('Latitudine').fill('45,4642');
  await place.getByLabel('Longitudine').fill('9,19');
  await expect(place.getByRole('link', { name: 'Apri su OpenStreetMap' })).toHaveAttribute('href', /mlat=45\.4642&mlon=9\.19/);
  await place.getByLabel('Raggio in metri').fill('500');
  await therapist.page.getByRole('button', { name: 'Rivedi e proponi' }).click();
  const review = therapist.page.getByRole('dialog', { name: 'Proporre questi vincoli?' });
  await expect(review.getByText('Alex deve attendere 2 ore dall’apertura di un messaggio prima di rispondere.')).toBeVisible();
  await expect(review.getByText('Sam può aprire i messaggi solo entro 500 metri dal luogo concordato.')).toBeVisible();
  await review.getByRole('button', { name: 'Proponi' }).click();
  await expect(therapist.page.getByText('Nessuno l’ha ancora approvata.')).toBeVisible();
  await expect(therapist.page.getByText('Potrai proporre nuovi vincoli quando la proposta in attesa sarà chiusa.')).toBeVisible();

  // Alex approves from the "Da fare"; Sam's approval applies them at once.
  await alex.page.goto('/');
  await alex.page.getByRole('link', { name: 'Una proposta di vincoli aspetta la tua risposta.' }).click();
  await alex.page.getByRole('button', { name: 'Approva' }).click();
  await expect(alex.page.getByRole('dialog').getByText('Approvando, accetti questi vincoli: entreranno in vigore quando li avrà approvati anche Sam.')).toBeVisible();
  await alex.page.getByRole('dialog').getByRole('button', { name: 'Approva' }).click();
  await expect(alex.page.getByText('Hai approvato: manca l’approvazione di Sam.')).toBeVisible();

  await sam.page.goto(`/conversazioni/${conversation}/vincoli`);
  await sam.page.getByRole('button', { name: 'Approva' }).click();
  await expect(sam.page.getByRole('dialog').getByText('Approvando, questi vincoli entrano subito in vigore al posto di quelli attuali.')).toBeVisible();
  await sam.page.getByRole('dialog').getByRole('button', { name: 'Approva' }).click();
  await expect(sam.page.getByText(/^Approvata da entrambi il /)).toBeVisible();
  await expect(sam.page.getByRole('heading', { name: 'In vigore' }).locator('..').getByRole('link', { name: 'Apri su OpenStreetMap' }), 'Sam sees the place').toBeVisible();

  // Alex sees what binds Sam, not where: the place doesn't even reach Alex's browser.
  await alex.page.reload();
  const inForce = alex.page.getByRole('heading', { name: 'In vigore' }).locator('..');
  await expect(inForce.getByText('Devi attendere 2 ore dall’apertura di un messaggio prima di rispondere.')).toBeVisible();
  await expect(inForce.getByText('Sam può aprire i messaggi solo entro 500 metri dal luogo concordato.')).toBeVisible();
  await expect(alex.page.getByRole('link', { name: 'Apri su OpenStreetMap' })).toHaveCount(0);
  const seen = JSON.stringify((await call(alex.page, 'GET', `/api/conversations/${conversation}/constraints`)).json);
  expect(seen).not.toContain(String(HOME.latitude));
  expect(seen).not.toContain(String(HOME.longitude));

  // They bind: after opening Sam's message, Alex waits two hours.
  await sendThroughTheApi(sam, conversation, 0, [{ type: 'SIMPLE', title: 'Ti scrivo', body: 'Con calma.' }]);
  await alex.page.goto(`/conversazioni/${conversation}/discorsi/0`);
  await expect(alex.page.getByText('Da quando lo apri dovrai attendere 2 ore prima di rispondere.')).toBeVisible();
  await alex.page.getByRole('button', { name: 'Apri' }).click();
  await expect(alex.page.getByText(/^Potrai rispondere dalle /)).toBeVisible();
  await expect(alex.page.getByRole('button', { name: 'Scrivi la risposta' })).toHaveCount(0);
  await close();
});

test('proposals rejected and withdrawn stay as they ended', async ({ page, browser }) =>
{
  const { therapist, alex, sam, couple, close } = await setUpCouple(browser, page);
  const conversation = await startConversation(alex, sam, couple);
  const evening = { assignee: await idOf(alex), type: 'TIME', notBefore: '18:00' };
  const requests = `/api/conversations/${conversation}/constraints/requests`;
  expect((await call(therapist.page, 'POST', requests, { constraints: [evening] })).status).toBe(201);

  // Alex approves, then changes their mind before Sam does.
  await alex.page.goto(`/conversazioni/${conversation}/vincoli`);
  await expect(alex.page.getByText('Puoi scrivere solo dalle 18:00 a mezzanotte.')).toBeVisible();
  await confirm(alex.page, 'Approva');
  await expect(alex.page.getByText('Hai approvato: manca l’approvazione di Sam.')).toBeVisible();
  await confirm(alex.page, 'Rifiuta', 'Rifiuta la proposta');
  await expect(alex.page.getByText(/^Rifiutata da te il /)).toBeVisible();
  await expect(alex.page.getByText('Nessun vincolo in vigore.')).toBeVisible();

  // The therapist proposes again, then withdraws it.
  expect((await call(therapist.page, 'POST', requests, { constraints: [evening] })).status).toBe(201);
  await therapist.page.goto(`/conversazioni/${conversation}/vincoli`);
  await confirm(therapist.page, 'Ritira la proposta');
  await expect(therapist.page.getByText(/^Ritirata il /)).toBeVisible();
  await expect(therapist.page.getByRole('button', { name: 'Proponi nuovi vincoli' })).toBeVisible();

  await sam.page.goto(`/conversazioni/${conversation}/vincoli`);
  await expect(sam.page.getByText(/^Rifiutata da Alex il /)).toBeVisible();
  await expect(sam.page.getByText(/^Ritirata il /)).toBeVisible();
  await expect(sam.page.getByRole('button', { name: 'Approva' })).toHaveCount(0);

  // Nothing binds Alex: a message after opening can be written at once.
  await sendThroughTheApi(sam, conversation, 0, [{ type: 'SIMPLE', title: 'Ti scrivo', body: 'Con calma.' }]);
  await openThroughTheApi(alex, conversation, 0);
  await alex.page.goto(`/conversazioni/${conversation}/discorsi/0`);
  await expect(alex.page.getByText('Tocca a te: puoi rispondere.')).toBeVisible();
  await close();
});
