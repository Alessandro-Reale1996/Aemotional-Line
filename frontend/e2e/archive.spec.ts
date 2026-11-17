import { expect, test } from './fixtures';
import { call, openDiscussionThroughTheApi, openThroughTheApi, sendThroughTheApi, setUpCouple, startConversation } from './helpers';

/**
 * The archive (CLAUDE.md, F10), on the jar, through the interface: a word found without revealing what Alex hasn't
 * opened and never put in the address, the period with a closed envelope, a skipped question, a topic across a specific
 * discussion, the concluded stipule. The messages and the stipule are set up through the API.
 */

function now(): string
{
  const date = new Date();

  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

test('a word found without revealing a closed envelope, the period and a skipped question', async ({ page, browser }) =>
{
  const { alex, sam, couple, close } = await setUpCouple(browser, page);
  const conversation = await startConversation(alex, sam, couple);
  await sendThroughTheApi(alex, conversation, 0, [{ type: 'QUESTION', title: 'Dove andiamo?', body: 'Parliamo delle vacanze.' }]);
  await openThroughTheApi(sam, conversation, 0);
  // Sam answers without answering the question, and Alex doesn't open it.
  await sendThroughTheApi(sam, conversation, 0, [{ type: 'SIMPLE', title: 'Il mare', body: 'Le vacanze al mare, come sempre.' }]);

  await alex.page.goto('/');
  await alex.page.getByRole('link', { name: 'Archivio' }).click();
  await expect(alex.page.getByText('I messaggi che non hai ancora aperto non compaiono nella ricerca.')).toBeVisible();
  await alex.page.getByLabel('Cerca una parola o una frase').fill('VACANZE');
  await alex.page.getByRole('button', { name: 'Cerca' }).click();
  await expect(alex.page.getByRole('heading', { name: 'Nei messaggi (1)' })).toBeVisible();
  await expect(alex.page.getByRole('region', { name: 'Dove andiamo?' }).locator('mark')).toHaveText('vacanze');
  await expect(alex.page.getByText('Il mare')).toHaveCount(0);
  expect(alex.page.url()).not.toMatch(/vacanze/i);

  // Back from a topic history, the search is still there.
  await alex.page.getByRole('region', { name: 'Dove andiamo?' }).getByRole('link', { name: 'Storia dell’argomento' }).click();
  await expect(alex.page.getByText('Parte da «Dove andiamo?»')).toBeVisible();
  await alex.page.goBack();
  await expect(alex.page.getByRole('heading', { name: 'Nei messaggi (1)' })).toBeVisible();
  await expect(alex.page.getByLabel('Cerca una parola o una frase')).toHaveValue('VACANZE');

  // The last seven days: Alex's own message, Sam's as an envelope.
  await alex.page.getByRole('link', { name: 'Per periodo' }).click();
  await expect(alex.page.getByText(/^Discorso principale · Tu · /)).toBeVisible();
  await expect(alex.page.getByText(/^Discorso principale · Messaggio di Sam · .+ · da aprire$/)).toBeVisible();
  expect(new URL(alex.page.url()).search).toBe('');
  await alex.page.getByLabel('Dal', { exact: true }).fill('2020-01-01');
  await alex.page.getByLabel('Al', { exact: true }).fill('2020-01-31');
  await alex.page.getByRole('button', { name: 'Mostra' }).click();
  await expect(alex.page.getByText('Nessun messaggio in questo periodo.')).toBeVisible();
  expect(new URL(alex.page.url()).search).toBe('?dal=2020-01-01&al=2020-01-31');
  await alex.page.goBack();
  await expect(alex.page.getByText(/^Discorso principale · Tu · /)).toBeVisible();

  // Sam's reply skipped Alex's question for good.
  await alex.page.getByRole('link', { name: 'Domande senza risposta' }).click();
  const skipped = alex.page.getByRole('heading', { name: 'Lasciate senza risposta' }).locator('..');
  await expect(skipped.getByText('Dove andiamo?')).toBeVisible();
  await close();
});

test('a topic across a specific discussion, and the concluded stipule', async ({ page, browser }) =>
{
  const { alex, sam, couple, close } = await setUpCouple(browser, page);
  const conversation = await startConversation(alex, sam, couple);
  await sendThroughTheApi(sam, conversation, 0, [{ type: 'SIMPLE', title: 'Il mare mi calma', body: 'Vorrei andare al mare.' }]);
  await openThroughTheApi(alex, conversation, 0);
  const view = (await call(alex.page, 'GET', `/api/conversations/${conversation}`)).json as { discussions: { messages: { paragraphs: { id: string }[] }[] }[] };
  const origin = view.discussions[0]?.messages[0]?.paragraphs[0]?.id ?? '';
  const number = await openDiscussionThroughTheApi(alex, sam, conversation, 'Le vacanze', 'Dove andare, e con chi', origin);
  const first = await call(alex.page, 'POST', `/api/conversations/${conversation}/discussions/${number}/messages`, {
    paragraphs: [{ type: 'SIMPLE', title: 'Al mare a luglio', subtitle: null, body: 'Partiamo a luglio.', references: [origin], selfCitations: [], question: null }],
    localTime: now(),
    position: null,
  });
  expect(first.status).toBe(201);
  // A change of the agreement Alex refuses: a stipula that failed.
  const change = await call(sam.page, 'POST', `/api/couples/${couple}/agreement-negotiations`, { text: 'Ci scriviamo ogni giorno.' });
  expect((await call(alex.page, 'POST', `/api/negotiations/${(change.json as { id: string }).id}/refuse-request`)).status).toBe(204);

  await alex.page.goto(`/conversazioni/${conversation}/discorsi/0`);
  await alex.page.getByRole('region', { name: 'Il mare mi calma' }).getByRole('link', { name: 'Storia dell’argomento' }).click();
  await expect(alex.page.getByText('Parte da «Il mare mi calma»')).toBeVisible();
  await expect(alex.page.getByText('Comprende i paragrafi collegati a questo nella conversazione principale e tutti i discorsi specifici nati da loro.')).toBeVisible();
  await expect(alex.page.getByText(/^Le vacanze · Tu · /)).toBeVisible();
  const reply = alex.page.getByRole('region', { name: 'Al mare a luglio' });
  await expect(reply.getByText('Risponde a «Il mare mi calma»')).toBeVisible();
  await reply.getByRole('link', { name: 'Vai al paragrafo' }).click();
  await expect(alex.page.getByRole('heading', { name: 'Le vacanze', level: 1 })).toBeVisible();

  await alex.page.goto(`/conversazioni/${conversation}`);
  await alex.page.getByRole('link', { name: 'Archivio' }).last().click();
  await alex.page.getByRole('link', { name: 'Stipule concluse' }).click();
  const took = alex.page.getByRole('heading', { name: 'Con effetto' }).locator('..');
  await expect(took.getByRole('article')).toHaveCount(2);
  await expect(took.getByRole('link', { name: '«Le vacanze»' })).toBeVisible();
  const failed = alex.page.getByRole('heading', { name: 'Non riuscite' }).locator('..');
  await expect(failed.getByText(/^Modifica dell’accordo · Rifiutata da te il /)).toBeVisible();
  await failed.getByText('Mostra le proposte (1)').click();
  await expect(failed.getByText('Ci scriviamo ogni giorno.')).toBeVisible();
  await close();
});
