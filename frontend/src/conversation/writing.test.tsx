import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Constraint, Conversation, Message, Negotiation } from '../api/client';
import { constraint, CONVERSATION, conversationWith, message, paragraph, partner, patient, talkingCouple, therapist } from '../api/testing';
import { renderApp } from '../app/testing';
import { discardUnsentText } from '../session/unsent';
import { ABANDON_TEXT, ANSWERED_ELSEWHERE, IRREVERSIBLE, MAX_REACHED } from './Composer';

const HOME = { latitude: 45.4642, longitude: 9.19 };
const mine = paragraph(1, 'Il mio primo pensiero');
const theirs = paragraph(2, 'La sua risposta', { references: [mine.id] });
const question = paragraph(3, 'Mi spieghi?', { type: 'QUESTION' });

/** Alex wrote, Sam answered with a paragraph and a question, Alex opened it. */
function exchange(): Conversation
{
  return conversationWith([
    message(1, patient.id, [mine]),
    message(2, partner.id, [theirs, question], { readAt: '2026-11-16T08:00:00Z' }),
  ]);
}

function renderWriting(first: Conversation, inForce: Constraint[] = [], more: (request: Request, path: string) => unknown = () => undefined,
  negotiations: Negotiation[] = [], account = patient)
{
  const state = { conversation: first };
  const rendered = renderApp({
    path: `/conversazioni/${CONVERSATION}/discorsi/0`,
    account,
    answer: (request, path) =>
    {
      const given = more(request, path);

      if (given !== undefined)
      {
        return given;
      }

      if (path === `GET /api/conversations/${CONVERSATION}`)
      {
        return state.conversation;
      }

      if (path.endsWith('/constraints'))
      {
        return { inForce, requests: [] };
      }

      if (path.endsWith('/negotiations'))
      {
        return negotiations;
      }

      return path === 'GET /api/me/couples' ? [talkingCouple] : undefined;
    },
  });

  return { ...rendered, state };
}

function geolocation(where: { latitude: number; longitude: number })
{
  Object.defineProperty(navigator, 'geolocation', {
    configurable: true,
    value: { getCurrentPosition: (ok: PositionCallback) => ok({ coords: where } as GeolocationPosition) },
  });
}

function card(name: string): HTMLElement
{
  return screen.getByRole('region', { name });
}

beforeEach(() =>
{
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-16T09:00:00Z'));
});

afterEach(() =>
{
  vi.useRealTimers();
  discardUnsentText();
  Reflect.deleteProperty(navigator, 'geolocation');
});

describe('starting to write', () =>
{
  it('is offered only when one can write', async () =>
  {
    renderWriting(exchange());
    expect(await screen.findByRole('button', { name: 'Scrivi la risposta' })).toBeTruthy();
  });

  it('is not offered during a wait', async () =>
  {
    const delay = [constraint(patient.id, { type: 'REPLY_DELAY', delay: 'PT2H' })];
    const waiting = renderWriting(exchange(), delay);
    expect(await screen.findByText(/^Potrai rispondere dalle /)).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^Scrivi|^Rispondi/ })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Approfondisci' })).toBeNull();
    waiting.router.dispose();
  });

  it('is not offered to the therapist', async () =>
  {
    renderWriting(exchange(), [], undefined, [], therapist);
    expect(await screen.findByText('Tocca ad Alex.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^Scrivi|^Rispondi/ })).toBeNull();
  });

  it('checks a writing place in the browser, and opens the area only from there', async () =>
  {
    const place = constraint(patient.id, { type: 'LOCATION', scope: 'WRITE', latitude: HOME.latitude, longitude: HOME.longitude, radiusMetres: 500, direction: 'WITHIN' });
    geolocation({ latitude: 41.9, longitude: 12.5 });
    const { user, server } = renderWriting(exchange(), [place]);

    await user.click(await screen.findByRole('button', { name: 'Scrivi la risposta' }));
    expect(await screen.findByText('Per scrivere devi trovarti entro 500 metri dal luogo concordato.')).toBeTruthy();
    expect(screen.queryByRole('region', { name: 'La tua risposta' })).toBeNull();

    geolocation(HOME);
    await user.click(screen.getByRole('button', { name: 'Scrivi la risposta' }));
    expect(await screen.findByRole('region', { name: 'La tua risposta' })).toBeTruthy();
    expect(server.paths().filter((path) => path.startsWith('POST'))).toEqual([]);
  });
});

describe('the cards', () =>
{
  it('answer a question with a pointed answer, once, and count the questions answered', async () =>
  {
    const { user } = renderWriting(exchange());

    const asked = (await screen.findByRole('region', { name: 'Mi spieghi?' }));
    await user.click(within(asked).getByRole('button', { name: 'Rispondi' }));

    const pointed = card('Risposta puntuale 1');
    expect(within(pointed).getByText('In risposta a «Mi spieghi?»')).toBeTruthy();
    expect(within(pointed).queryByLabelText('Titolo')).toBeNull();
    expect(within(asked).queryByRole('button', { name: 'Rispondi' }), 'a question gets one answer').toBeNull();
    expect(screen.getByText('Domande a cui rispondi: 1 di 1')).toBeTruthy();
  });

  it('answer a paragraph with a linked card, whose links can change', async () =>
  {
    const { user } = renderWriting(exchange());

    await user.click(within(await screen.findByRole('region', { name: 'La sua risposta' })).getByRole('button', { name: 'Rispondi' }));
    const simple = card('Paragrafo 1');
    expect(within(simple).getByText('Risponde a «La sua risposta»')).toBeTruthy();
    expect(within(simple).getByLabelText('Sottotitolo (facoltativo)')).toBeTruthy();
    expect(within(simple).queryByRole('button', { name: 'Collega a un paragrafo' }), 'a question is answered only by a pointed answer').toBeNull();

    await user.click(within(simple).getByRole('button', { name: 'Togli il collegamento' }));
    expect(within(simple).queryByText('Risponde a «La sua risposta»')).toBeNull();
    await user.click(within(simple).getByRole('button', { name: 'Collega a un paragrafo' }));
    await user.click(await screen.findByRole('button', { name: 'La sua risposta' }));
    expect(within(simple).getByText('Risponde a «La sua risposta»')).toBeTruthy();
  });

  it('cite the user own paragraphs of the topic', async () =>
  {
    const { user } = renderWriting(exchange());

    await user.click(within(await screen.findByRole('region', { name: 'La sua risposta' })).getByRole('button', { name: 'Rispondi' }));
    const simple = card('Paragrafo 1');
    await user.click(within(simple).getByRole('button', { name: 'Cita un tuo paragrafo' }));
    await user.click(await screen.findByRole('button', { name: 'Il mio primo pensiero' }));

    expect(within(simple).getByText('Richiama «Il mio primo pensiero»')).toBeTruthy();
  });

  it('are at most twenty', async () =>
  {
    const { user } = renderWriting(exchange());

    await user.click(await screen.findByRole('button', { name: 'Scrivi la risposta' }));

    for (let added = 0; added < 20; added += 1)
    {
      await user.click(screen.getByRole('button', { name: 'Aggiungi un paragrafo' }));
    }

    expect(screen.getByText(MAX_REACHED)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Aggiungi un paragrafo' })).toBeNull();
  });

  it('are given up only after a confirmation', async () =>
  {
    const { user } = renderWriting(exchange());

    await user.click(await screen.findByRole('button', { name: 'Scrivi la risposta' }));
    await user.click(screen.getByRole('button', { name: 'Aggiungi un paragrafo' }));
    await user.type(within(card('Paragrafo 1')).getByLabelText('Titolo'), 'Un pensiero');
    await user.click(screen.getByRole('button', { name: 'Rinuncia alla risposta' }));
    expect(await screen.findByText(ABANDON_TEXT)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Rinuncia' }));

    await waitFor(() => expect(screen.queryByRole('region', { name: 'La tua risposta' })).toBeNull());
    expect(screen.getByRole('button', { name: 'Scrivi la risposta' })).toBeTruthy();
  });
});

describe('the paragraphs one can not answer here', () =>
{
  it('lead to the discussion that expands them', async () =>
  {
    const conversation = exchange();
    conversation.discussions.push({ id: 1, title: 'Le spiegazioni', subtitle: 'Con calma', closed: false, originParagraphId: question.id, lastSender: null, messages: [] });
    renderWriting(conversation);

    const asked = await screen.findByRole('region', { name: 'Mi spieghi?' });
    expect(within(asked).getByRole('link', { name: '«Le spiegazioni»' })).toHaveProperty('pathname', `/conversazioni/${CONVERSATION}/discorsi/1`);
    expect(within(asked).queryByRole('button', { name: 'Rispondi' })).toBeNull();
  });

  it('can be expanded, unless a request is already in progress', async () =>
  {
    const pending = { originParagraphId: question.id } as Negotiation;
    renderWriting(exchange(), [], undefined, [pending]);

    const said = await screen.findByRole('region', { name: 'La sua risposta' });
    expect(within(said).getByRole('link', { name: 'Approfondisci' }).getAttribute('href'))
      .toBe(`/conversazioni/${CONVERSATION}/nuovo-discorso?paragrafo=${theirs.id}`);
    expect(within(screen.getByRole('region', { name: 'Mi spieghi?' })).getByText('Richiesta di approfondimento in corso')).toBeTruthy();
  });

  it('is the only one the first message of a discussion born from it answers', async () =>
  {
    const conversation = exchange();
    conversation.discussions.push({ id: 1, title: 'Il tuo pensiero', subtitle: 'Approfondiamo', closed: false, originParagraphId: theirs.id, lastSender: null, messages: [] });
    const { user } = renderApp({
      path: `/conversazioni/${CONVERSATION}/discorsi/1`,
      account: patient,
      answer: (_, path) => (path === `GET /api/conversations/${CONVERSATION}` ? conversation : path.endsWith('/constraints') ? { inForce: [], requests: [] }
        : path === 'GET /api/me/couples' ? [talkingCouple] : undefined),
    });
    await user.click(await screen.findByRole('button', { name: 'Scrivi il primo messaggio' }));
    await screen.findByRole('region', { name: 'La tua risposta' });
    await user.click(screen.getByRole('button', { name: 'Aggiungi un paragrafo' }));

    const simple = await screen.findByRole('region', { name: 'Paragrafo 1' });
    expect(within(simple).getByText('Risponde a «La sua risposta»')).toBeTruthy();
    expect(within(simple).queryByRole('button', { name: 'Togli il collegamento' }), 'the link to the origin is fixed').toBeNull();
  });
});

describe('the review', () =>
{
  async function writeAnAnswer(user: ReturnType<typeof renderApp>['user'])
  {
    await user.click(within(await screen.findByRole('region', { name: 'La sua risposta' })).getByRole('button', { name: 'Rispondi' }));
    await user.click(screen.getByRole('button', { name: 'Rivedi e invia' }));
    expect(await screen.findByText('Il paragrafo 1 non ha il titolo.')).toBeTruthy();
    await user.type(within(card('Paragrafo 1')).getByLabelText('Titolo'), 'Ti rispondo');
    await user.type(within(card('Paragrafo 1')).getByLabelText('Testo'), 'Prima riga.\nSeconda riga.');
    await user.click(screen.getByRole('button', { name: 'Rivedi e invia' }));
  }

  it('shows the message as Sam will read it, lists the questions left unanswered, and sends', async () =>
  {
    const { user, server, state } = renderWriting(exchange(), [], (_, path) =>
    {
      if (path.endsWith('/previews'))
      {
        return { questionsLeftUnanswered: [question.id], blockingDiscussions: [] };
      }

      if (path.endsWith('/messages'))
      {
        const sent: Message = message(3, patient.id, [paragraph(9, 'Ti rispondo', { references: [theirs.id] })]);
        state.conversation = conversationWith([...exchange().discussions[0]?.messages ?? [], sent]);

        return Response.json({ id: sent.id }, { status: 201 });
      }

      return undefined;
    });

    await writeAnAnswer(user);
    const review = await screen.findByRole('dialog', { name: 'Rivedi il messaggio' });
    expect(within(review).getByText('Così lo leggerà Sam:')).toBeTruthy();
    expect(within(review).getByText('Ti rispondo')).toBeTruthy();
    expect(within(review).getByText('Risponde a «La sua risposta»')).toBeTruthy();
    expect(await within(review).findByText('Non rispondi a queste domande: «Mi spieghi?». Puoi tornare a scrivere o inviare comunque.')).toBeTruthy();
    expect(within(review).getByText(IRREVERSIBLE)).toBeTruthy();
    await user.click(within(review).getByRole('button', { name: 'Invia comunque' }));

    expect(await screen.findByText('Hai inviato l’ultimo messaggio: ora tocca a Sam.')).toBeTruthy();
    expect(screen.queryByRole('region', { name: 'La tua risposta' })).toBeNull();
    expect(await server.requests.find((request) => request.url.endsWith('/messages'))?.json()).toEqual({
      paragraphs: [{ type: 'SIMPLE', title: 'Ti rispondo', subtitle: null, body: 'Prima riga.\nSeconda riga.', references: [theirs.id], selfCitations: [], question: null }],
      localTime: '10:00',
      position: null,
    });
  });

  it('stops the sending of a paragraph answered in another discussion, and says where', async () =>
  {
    // The page shows "Se ne parla in" instead of "Rispondi": only stale data can get here, so the server's preview decides.
    const conversation = exchange();
    conversation.discussions.push({ id: 1, title: 'Le spiegazioni', subtitle: 'Con calma', closed: false, originParagraphId: mine.id, lastSender: null, messages: [] });
    const { user } = renderWriting(conversation, [], (_, path) => (path.endsWith('/previews') ? { questionsLeftUnanswered: [], blockingDiscussions: [1, 7] } : undefined));

    await writeAnAnswer(user);
    const review = await screen.findByRole('dialog', { name: 'Rivedi il messaggio' });

    expect(await within(review).findByText(/^Al paragrafo «Il mio primo pensiero» si risponde in/)).toBeTruthy();
    expect(within(review).getByRole('link', { name: '«Le spiegazioni»' })).toHaveProperty('pathname', `/conversazioni/${CONVERSATION}/discorsi/1`);
    expect(within(review).getByText(ANSWERED_ELSEWHERE), 'a discussion the page does not know yet').toBeTruthy();
    expect(within(review).getByRole('button', { name: 'Invia' })).toHaveProperty('disabled', true);
  });

  it('sends the position again with the message, from the writing place', async () =>
  {
    const place = constraint(patient.id, { type: 'LOCATION', scope: 'WRITE', latitude: HOME.latitude, longitude: HOME.longitude, radiusMetres: 500, direction: 'WITHIN' });
    geolocation(HOME);
    const { user, server } = renderWriting(exchange(), [place], (_, path) =>
    {
      if (path.endsWith('/previews'))
      {
        return { questionsLeftUnanswered: [], blockingDiscussions: [] };
      }

      return path.endsWith('/messages') ? Response.json({ id: 'x' }, { status: 201 }) : undefined;
    });

    await writeAnAnswer(user);
    await user.click(await within(await screen.findByRole('dialog', { name: 'Rivedi il messaggio' })).findByRole('button', { name: 'Invia' }));

    await waitFor(() => expect(server.paths()).toContain(`POST /api/conversations/${CONVERSATION}/discussions/0/messages`));
    expect(await server.requests.find((request) => request.url.endsWith('/messages'))?.json()).toMatchObject({ position: HOME });
  });
});
