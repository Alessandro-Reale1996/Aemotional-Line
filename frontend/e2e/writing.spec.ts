import type { Page } from '@playwright/test';

import { expect, test } from './fixtures';
import { applyConstraints, call, idOf, openThroughTheApi, sendThroughTheApi, setUpCouple, startConversation } from './helpers';

/**
 * Writing (CLAUDE.md, F8), on the jar, through the interface: messages written card by card, answers, a question left
 * unanswered on purpose, a self-citation, a writing place, a paragraph answered in the discussion that expands it.
 */
const HOME = { latitude: 45.4642, longitude: 9.19 };
const ROME = { latitude: 41.9028, longitude: 12.4964 };

async function fillCard(page: Page, name: string, title: string | undefined, text: string)
{
  const card = page.getByRole('region', { name });

  if (title !== undefined)
  {
    await card.getByLabel('Titolo', { exact: true }).fill(title);
  }

  await card.getByLabel('Testo', { exact: true }).fill(text);
}

async function reviewAndSend(page: Page, button: 'Invia' | 'Invia comunque')
{
  await page.getByRole('button', { name: 'Rivedi e invia' }).click();
  const review = page.getByRole('dialog', { name: 'Rivedi il messaggio' });
  await expect(review.getByText('Una volta inviato, il messaggio non si può più modificare.')).toBeVisible();
  await review.getByRole('button', { name: button, exact: true }).click();
  await expect(page.getByText(/^Hai inviato l’ultimo messaggio: ora tocca/)).toBeVisible();
}

test('an exchange written card by card: answers, a question skipped, a self-citation', async ({ page, browser }) =>
{
  const { alex, sam, couple, close } = await setUpCouple(browser, page);
  const conversation = await startConversation(alex, sam, couple);
  const discussion = `/conversazioni/${conversation}/discorsi/0`;

  // Alex writes the first message: a paragraph and a question.
  await alex.page.goto(discussion);
  await alex.page.getByRole('button', { name: 'Scrivi il primo messaggio' }).click();
  await alex.page.getByRole('button', { name: 'Aggiungi un paragrafo' }).click();
  await fillCard(alex.page, 'Paragrafo 1', 'Il mio primo pensiero', 'Prima riga.\nSeconda riga.');
  await alex.page.getByRole('button', { name: 'Aggiungi una domanda' }).click();
  await fillCard(alex.page, 'Domanda 2', 'Come stai?', 'Dimmi davvero.');
  await reviewAndSend(alex.page, 'Invia');

  // Sam opens it, answers the paragraph, and leaves the question for later, knowingly.
  await sam.page.goto(discussion);
  await sam.page.getByRole('button', { name: 'Apri' }).click();
  await sam.page.getByRole('region', { name: 'Il mio primo pensiero' }).getByRole('button', { name: 'Rispondi' }).click();
  await fillCard(sam.page, 'Paragrafo 1', 'Ti ho letto', 'Ci penso.');
  await expect(sam.page.getByText('Domande a cui rispondi: 0 di 1')).toBeVisible();
  await sam.page.getByRole('button', { name: 'Rivedi e invia' }).click();
  await expect(sam.page.getByText('Non rispondi a queste domande: «Come stai?». Puoi tornare a scrivere o inviare comunque.')).toBeVisible();
  await sam.page.getByRole('dialog').getByRole('button', { name: 'Invia comunque' }).click();
  await expect(sam.page.getByText('Hai inviato l’ultimo messaggio: ora tocca ad Alex.')).toBeVisible();

  // Alex answers, citing his own first paragraph, which is in the same topic.
  await alex.page.reload();
  await alex.page.getByRole('button', { name: 'Apri' }).click();
  await alex.page.getByRole('region', { name: 'Ti ho letto' }).getByRole('button', { name: 'Rispondi' }).click();
  const answer = alex.page.getByRole('region', { name: 'Paragrafo 1' });
  await answer.getByRole('button', { name: 'Cita un tuo paragrafo' }).click();
  await alex.page.getByRole('button', { name: 'Il mio primo pensiero' }).click();
  await expect(answer.getByText('Richiama «Il mio primo pensiero»')).toBeVisible();
  await fillCard(alex.page, 'Paragrafo 1', 'Allora aspetto', 'Con calma.');
  await reviewAndSend(alex.page, 'Invia');

  await sam.page.reload();
  await sam.page.getByRole('button', { name: 'Apri' }).click();
  const letter = sam.page.getByRole('region', { name: 'Allora aspetto' });
  await expect(letter.getByRole('link', { name: '«Ti ho letto»' })).toBeVisible();
  await expect(letter.getByRole('link', { name: '«Il mio primo pensiero»' })).toBeVisible();
  await close();
});

test('a writing place, checked when the writing starts and again when sending', async ({ page, browser, context }) =>
{
  const { therapist, alex, sam, couple, close } = await setUpCouple(browser, page);
  const conversation = await startConversation(alex, sam, couple);
  await applyConstraints(therapist, [alex, sam], conversation, [{
    assignee: await idOf(alex), type: 'LOCATION', scope: 'WRITE', latitude: HOME.latitude, longitude: HOME.longitude, radiusMetres: 500, direction: 'WITHIN',
  }]);
  await sendThroughTheApi(sam, conversation, 0, [{ type: 'SIMPLE', title: 'Ti scrivo', body: 'Rispondimi da casa.' }]);
  await openThroughTheApi(alex, conversation, 0);
  await context.grantPermissions(['geolocation']);
  const sent: unknown[] = [];
  alex.page.on('request', (request) =>
  {
    if (request.method() === 'POST' && request.url().endsWith('/messages'))
    {
      sent.push(request.postDataJSON());
    }
  });

  await context.setGeolocation(ROME);
  await alex.page.goto(`/conversazioni/${conversation}/discorsi/0`);
  await alex.page.getByRole('button', { name: 'Scrivi la risposta' }).click();
  await expect(alex.page.getByText('Per scrivere devi trovarti entro 500 metri dal luogo concordato.')).toBeVisible();

  await context.setGeolocation(HOME);
  await alex.page.getByRole('button', { name: 'Scrivi la risposta' }).click();
  await alex.page.getByRole('button', { name: 'Aggiungi un paragrafo' }).click();
  await fillCard(alex.page, 'Paragrafo 1', 'Da casa', 'Ti rispondo da qui.');
  await reviewAndSend(alex.page, 'Invia');
  expect(sent).toHaveLength(1);
  expect(sent[0]).toMatchObject({ position: HOME });
  await close();
});

test('a paragraph expanded in a specific discussion is answered there', async ({ page, browser }) =>
{
  const { alex, sam, couple, close } = await setUpCouple(browser, page);
  const conversation = await startConversation(alex, sam, couple);
  await sendThroughTheApi(alex, conversation, 0, [{ type: 'SIMPLE', title: 'Le vacanze', body: 'Dove andiamo?' }]);
  await openThroughTheApi(sam, conversation, 0);
  const view = (await call(sam.page, 'GET', `/api/conversations/${conversation}`)).json as { discussions: { messages: { paragraphs: { id: string }[] }[] }[] };
  const paragraph = view.discussions[0]?.messages[0]?.paragraphs[0]?.id;

  // Sam asks to expand it, through the interface ("Approfondisci"); the stipula is concluded through the API.
  await sam.page.goto(`/conversazioni/${conversation}/discorsi/0`);
  await sam.page.getByRole('region', { name: 'Le vacanze' }).getByRole('link', { name: 'Approfondisci' }).click();
  await sam.page.getByLabel('Titolo', { exact: true }).fill('Le vacanze');
  await sam.page.getByLabel('Sottotitolo').fill('Dove e quando');
  await sam.page.getByRole('button', { name: 'Invia la richiesta' }).click();
  await expect(sam.page.getByText('Nasce da «Le vacanze»')).toBeVisible();
  const negotiation = new URL(sam.page.url()).pathname.split('/').at(-1);
  expect((await call(alex.page, 'POST', `/api/negotiations/${negotiation}/accept-request`)).status).toBe(204);
  expect((await call(sam.page, 'POST', `/api/negotiations/${negotiation}/send`, { justification: 'Ne parliamo a parte.' })).status).toBe(204);
  expect((await call(alex.page, 'POST', `/api/negotiations/${negotiation}/accept-proposal`)).status).toBe(200);

  // In the main discussion the paragraph leads there, instead of offering "Rispondi".
  await sam.page.goto(`/conversazioni/${conversation}/discorsi/0`);
  const expanded = sam.page.getByRole('region', { name: 'Le vacanze' });
  await expect(expanded.getByText('Se ne parla in')).toBeVisible();
  await expect(expanded.getByRole('button', { name: 'Rispondi' })).toHaveCount(0);
  await expanded.getByRole('link', { name: '«Le vacanze»' }).click();

  // There, the first message answers it, and the link can't be removed.
  await sam.page.getByRole('button', { name: 'Scrivi il primo messaggio' }).click();
  await sam.page.getByRole('button', { name: 'Aggiungi un paragrafo' }).click();
  const card = sam.page.getByRole('region', { name: 'Paragrafo 1' });
  await expect(card.getByText('Risponde a «Le vacanze»')).toBeVisible();
  await expect(card.getByRole('button', { name: 'Togli il collegamento' })).toHaveCount(0);
  await fillCard(sam.page, 'Paragrafo 1', 'Al mare', 'Io direi al mare.');
  await reviewAndSend(sam.page, 'Invia');
  expect(paragraph).toBeTruthy();
  await close();
});
