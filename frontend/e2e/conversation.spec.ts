import { expect, test } from './fixtures';
import { applyConstraints, idOf, openThroughTheApi, sendThroughTheApi, setUpCouple, startConversation } from './helpers';

/**
 * Reading (CLAUDE.md, F7), on the jar, through the interface: the letters, a closed envelope opened, no read receipts, a
 * real wait after the opening, a place to open a message. The messages and the constraints are set through the API.
 */
const HOME = { latitude: 45.4642, longitude: 9.19 };
const ROME = { latitude: 41.9028, longitude: 12.4964 };

test('a letter opened from the "Da fare", with no read receipt for its sender', async ({ page, browser }) =>
{
  const { therapist, alex, sam, couple, close } = await setUpCouple(browser, page);
  const conversation = await startConversation(alex, sam, couple);
  await sendThroughTheApi(sam, conversation, 0, [{ type: 'QUESTION', title: 'Come stai?', body: 'Prima riga.\nSeconda riga.' }]);

  await alex.page.goto('/');
  await alex.page.getByRole('link', { name: 'Discorso principale: 1 messaggio da aprire.' }).click();
  const envelope = alex.page.getByRole('article', { name: 'Messaggio di Sam, da aprire' });
  await expect(envelope).toBeVisible();
  await expect(alex.page.getByText('Come stai?')).toHaveCount(0);
  await expect(alex.page.getByText('Apri il messaggio di Sam per poter rispondere.')).toBeVisible();
  await envelope.getByRole('button', { name: 'Apri' }).click();

  const letter = alex.page.getByRole('article', { name: 'Messaggio di Sam' });
  await expect(letter.getByText('Come stai?')).toBeVisible();
  await expect(letter.getByText('Domanda')).toBeVisible();
  await expect(letter.getByText(/^L’hai aperto il /)).toBeVisible();
  await expect(alex.page.getByText('Tocca a te: puoi rispondere.')).toBeVisible();

  // The bar leads to the conversation too.
  await alex.page.getByRole('link', { name: 'Conversazione', exact: true }).click();
  await expect(alex.page.getByText('Ci scriviamo con calma.')).toBeVisible();

  // Sam never learns that it was opened; the therapist does.
  await sam.page.goto(`/conversazioni/${conversation}/discorsi/0`);
  await expect(sam.page.getByText('Hai inviato l’ultimo messaggio: ora tocca ad Alex.')).toBeVisible();
  await expect(sam.page.getByText(/aperto/i)).toHaveCount(0);
  await therapist.page.goto(`/conversazioni/${conversation}/discorsi/0`);
  await expect(therapist.page.getByText(/^Aperto da Alex il /)).toBeVisible();
  await expect(therapist.page.getByText('Tocca ad Alex.')).toBeVisible();
  await close();
});

test('a first message whatever the wait, then a real wait after the opening, over by itself', async ({ page, browser }) =>
{
  const { therapist, alex, sam, couple, close } = await setUpCouple(browser, page);
  const conversation = await startConversation(alex, sam, couple);
  await applyConstraints(therapist, [alex, sam], conversation, [{ assignee: await idOf(alex), type: 'REPLY_DELAY', delay: 'PT3S' }]);
  // The wait doesn't stop Alex from writing the first message: it answers nothing (fixed in F7).
  await sendThroughTheApi(alex, conversation, 0, [{ type: 'SIMPLE', title: 'Comincio io', body: 'Ti scrivo per primo.' }]);
  await openThroughTheApi(sam, conversation, 0);
  await sendThroughTheApi(sam, conversation, 0, [{ type: 'SIMPLE', title: 'Ti scrivo', body: 'Con calma.' }]);

  await alex.page.goto(`/conversazioni/${conversation}/discorsi/0`);
  await expect(alex.page.getByText('Da quando lo apri dovrai attendere 3 secondi prima di rispondere.')).toBeVisible();
  await alex.page.getByRole('button', { name: 'Apri' }).click();
  await expect(alex.page.getByText(/^Potrai rispondere dalle /)).toBeVisible();
  // No writing during the wait (frontend point 6): nothing to write with, until it is over.
  await expect(alex.page.getByRole('button', { name: 'Scrivi la risposta' })).toHaveCount(0);
  await expect(alex.page.getByRole('button', { name: 'Rispondi' })).toHaveCount(0);
  await expect(alex.page.getByText('Tocca a te: puoi rispondere.')).toBeVisible({ timeout: 10_000 });
  await expect(alex.page.getByRole('button', { name: 'Scrivi la risposta' })).toBeVisible();
  await close();
});

test('a place to open a message: checked in the browser, sent only from there', async ({ page, browser, context }) =>
{
  const { therapist, alex, sam, couple, close } = await setUpCouple(browser, page);
  const conversation = await startConversation(alex, sam, couple);
  await applyConstraints(therapist, [alex, sam], conversation, [{
    assignee: await idOf(alex), type: 'LOCATION', scope: 'READ', latitude: HOME.latitude, longitude: HOME.longitude, radiusMetres: 500, direction: 'WITHIN',
  }]);
  await sendThroughTheApi(sam, conversation, 0, [{ type: 'SIMPLE', title: 'Quando sei a casa', body: 'Leggila lì.' }]);
  await context.grantPermissions(['geolocation']);
  const openings: unknown[] = [];
  alex.page.on('request', (request) =>
  {
    if (request.url().endsWith('/open'))
    {
      openings.push(request.postDataJSON());
    }
  });

  await context.setGeolocation(ROME);
  await alex.page.goto(`/conversazioni/${conversation}/discorsi/0`);
  await alex.page.getByRole('button', { name: 'Apri' }).click();
  await expect(alex.page.getByText('Per aprirlo devi trovarti entro 500 metri dal luogo concordato.')).toBeVisible();
  expect(openings, 'nothing sent from outside the place').toEqual([]);

  await context.setGeolocation(HOME);
  await alex.page.getByRole('button', { name: 'Apri' }).click();
  await expect(alex.page.getByText('Quando sei a casa')).toBeVisible();
  expect(openings).toHaveLength(1);
  expect(openings[0]).toMatchObject({ position: HOME });
  await close();
});
