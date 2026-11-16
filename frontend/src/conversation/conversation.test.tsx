import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Constraint, Conversation, Message, Negotiation } from '../api/client';
import {
  constraint,
  CONVERSATION,
  conversationWith,
  emptySummary,
  message,
  paragraph,
  partner,
  patient,
  talkingCouple,
  therapist,
} from '../api/testing';
import { renderApp } from '../app/testing';
import { NO_CONVERSATION } from './ConversationPages';
import { POSITION_DENIED } from './Letters';

const question = paragraph(1, 'Come stai?', { type: 'QUESTION', body: 'Prima riga.\nSeconda riga.' });
const answer = paragraph(2, 'In risposta a «Come stai?»', { type: 'POINTED', references: [question.id] });
const HOME = { latitude: 45.4642, longitude: 9.19 };

/** A discussion page on a fake server whose conversation the test can change (an opening changes it). */
function renderDiscussion(account: typeof patient, first: Conversation, inForce: Constraint[] = [], more: (request: Request, path: string) => unknown = () => undefined)
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

      if (path === `GET /api/conversations/${CONVERSATION}/constraints`)
      {
        return { inForce, requests: [] };
      }

      return path === 'GET /api/me/couples' ? [talkingCouple] : undefined;
    },
  });

  return { ...rendered, state };
}

/** Sam's question, sealed in an envelope for Alex until opened. */
function samAsks(extra: Partial<Message> = {}): Message
{
  return message(1, partner.id, [question], extra);
}

function envelope(): Message
{
  return { ...samAsks(), paragraphs: [], closedEnvelope: true };
}

function geolocation(answer: (ok: PositionCallback, ko: PositionErrorCallback) => void)
{
  Object.defineProperty(navigator, 'geolocation', { configurable: true, value: { getCurrentPosition: answer } });
}

beforeEach(() =>
{
  // Only the date is fixed, at 10:00 in Rome: the timers stay real, as Testing Library needs.
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-16T09:00:00Z'));
});

afterEach(() =>
{
  vi.useRealTimers();
  Reflect.deleteProperty(navigator, 'geolocation');
});

describe('the conversation page', () =>
{
  it('shows the agreement in force and the discussions, with what is still to open', async () =>
  {
    const conversation: Conversation = {
      ...conversationWith([samAsks({ readAt: '2026-11-16T08:00:00Z' }), { ...message(2, partner.id, []), closedEnvelope: true }]),
    };
    conversation.discussions.push({ id: 1, title: 'Le vacanze', subtitle: 'Dove e con chi', closed: true, originParagraphId: null, lastSender: null, messages: [] });
    renderApp({
      path: `/conversazioni/${CONVERSATION}`,
      account: patient,
      answer: (_, path) => (path === `GET /api/conversations/${CONVERSATION}` ? conversation : path === 'GET /api/me/couples' ? [talkingCouple] : undefined),
    });

    expect(await screen.findByText('Ci scriviamo con calma.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Discorso principale' })).toHaveProperty('pathname', `/conversazioni/${CONVERSATION}/discorsi/0`);
    expect(screen.getByText('2 messaggi · 1 da aprire')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Le vacanze' })).toBeTruthy();
    expect(screen.getByText('Chiuso')).toBeTruthy();
    expect(screen.getByText('Ancora nessun messaggio.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Proponi un discorso specifico' })).toBeTruthy();
  });

  it('is reached from the bar: the conversation in progress, or none', async () =>
  {
    const first = renderApp({
      path: '/conversazione',
      account: patient,
      answer: (_, path) => (path === `GET /api/conversations/${CONVERSATION}` ? conversationWith([]) : path === 'GET /api/me/couples' ? [talkingCouple] : undefined),
    });
    await waitFor(() => expect(first.router.state.location.pathname).toBe(`/conversazioni/${CONVERSATION}`));
    first.router.dispose();
  });

  it('says when there is no conversation in progress', async () =>
  {
    renderApp({ path: '/conversazione', account: patient });

    expect(await screen.findByText(NO_CONVERSATION)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Conversazione' })).toHaveProperty('pathname', '/conversazione');
  });
});

describe('the letters', () =>
{
  it('show the sender, the full date, the paragraphs with their line breaks and the links between them', async () =>
  {
    const opened = samAsks({ readAt: '2026-11-16T08:00:00Z' });
    renderDiscussion(partner, conversationWith([opened, message(2, patient.id, [answer], { sentAt: '2026-11-16T08:30:00Z' })]));

    const letter = await screen.findByRole('article', { name: 'Messaggio di Sam' });
    expect(within(letter).getByText('16 novembre 2026, 18:30')).toBeTruthy();
    expect(within(letter).getByText('Domanda')).toBeTruthy();
    expect(within(letter).getByText('Prima riga.\nSeconda riga.', { normalizer: (text) => text })).toBeTruthy();
    const reply = screen.getByRole('article', { name: 'Messaggio di Alex' });
    expect(within(reply).getByRole('link', { name: '«Come stai?»' }).getAttribute('href'))
      .toBe(`/conversazioni/${CONVERSATION}/discorsi/0#p-${question.id}`);
  });

  it('say when the message was opened to its recipient and to the therapist, never to its sender', async () =>
  {
    const opened = conversationWith([samAsks({ readAt: '2026-11-16T08:00:00Z' })]);

    renderDiscussion(patient, opened);
    expect(await screen.findByText('L’hai aperto il 16 novembre 2026, 09:00')).toBeTruthy();
  });

  it('show the therapist who opened it', async () =>
  {
    renderDiscussion(therapist, conversationWith([samAsks({ readAt: '2026-11-16T08:00:00Z' })]));
    expect(await screen.findByText('Aperto da Alex il 16 novembre 2026, 09:00')).toBeTruthy();
  });

  it('say nothing of it to the sender, even if an opening reached the page', async () =>
  {
    // The API never gives the sender the opening (R6); the page doesn't show it either, should it ever arrive.
    renderDiscussion(partner, conversationWith([samAsks({ readAt: '2026-11-16T08:00:00Z' })]));
    await screen.findByRole('article', { name: 'Messaggio di Sam' });
    expect(screen.queryByText(/aperto/i)).toBeNull();
  });

  it('highlight the paragraph a link leads to', async () =>
  {
    renderApp({
      path: `/conversazioni/${CONVERSATION}/discorsi/0`,
      account: partner,
      answer: (_, path) =>
      {
        if (path === `GET /api/conversations/${CONVERSATION}`)
        {
          return conversationWith([samAsks({ readAt: '2026-11-16T08:00:00Z' }), message(2, patient.id, [answer])]);
        }

        return path.endsWith('/constraints') ? { inForce: [], requests: [] } : path === 'GET /api/me/couples' ? [talkingCouple] : undefined;
      },
    }).user.click(await screen.findByRole('link', { name: '«Come stai?»' }));

    await waitFor(() => expect(document.getElementById(`p-${question.id}`)?.className).toContain('paragraph-highlight'));
  });
});

describe('a closed envelope', () =>
{
  it('shows only who sent it and when, and opens with the local time', async () =>
  {
    const { user, server, state } = renderDiscussion(patient, conversationWith([envelope()]), [], (_, path) =>
    {
      if (path.endsWith('/open'))
      {
        state.conversation = conversationWith([samAsks({ readAt: '2026-11-16T09:00:00Z' })]);

        return samAsks({ readAt: '2026-11-16T09:00:00Z' });
      }

      return undefined;
    });

    const sealed = await screen.findByRole('article', { name: 'Messaggio di Sam, da aprire' });
    expect(within(sealed).getByText('Messaggio di Sam')).toBeTruthy();
    expect(screen.queryByText('Come stai?')).toBeNull();
    expect(screen.getByText('Apri il messaggio di Sam per poter rispondere.')).toBeTruthy();
    await user.click(within(sealed).getByRole('button', { name: 'Apri' }));

    expect(await screen.findByText('Come stai?')).toBeTruthy();
    expect(await server.requests.find((request) => request.url.endsWith('/open'))?.json()).toEqual({ localTime: '10:00', position: null });
    expect(screen.getByText('Tocca a te: puoi rispondere.')).toBeTruthy();
  });

  it('announces the wait the opening starts', async () =>
  {
    renderDiscussion(patient, conversationWith([envelope()]), [constraint(patient.id, { type: 'REPLY_DELAY', delay: 'PT2H' })]);

    expect(await screen.findByText('Da quando lo apri dovrai attendere 2 ore prima di rispondere.')).toBeTruthy();
  });

  it('waits for the reading time, without a button', async () =>
  {
    renderDiscussion(patient, conversationWith([envelope()]), [constraint(patient.id, { type: 'TIME', scope: 'READ', notBefore: '18:00:00' })]);

    expect(await screen.findByText('Potrai aprirlo dalle 18:00.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Apri' })).toBeNull();
  });

  it('checks the position in the browser first, and sends it only when it satisfies the constraint', async () =>
  {
    const place = constraint(patient.id, { type: 'LOCATION', scope: 'READ', latitude: HOME.latitude, longitude: HOME.longitude, radiusMetres: 500, direction: 'WITHIN' });
    let where = { latitude: 41.9028, longitude: 12.4964 };
    geolocation((ok) => ok({ coords: where } as GeolocationPosition));
    const { user, server } = renderDiscussion(patient, conversationWith([envelope()]), [place], (_, path) =>
      (path.endsWith('/open') ? samAsks({ readAt: '2026-11-16T09:00:00Z' }) : undefined));

    await user.click(await screen.findByRole('button', { name: 'Apri' }));
    expect(await screen.findByText('Per aprirlo devi trovarti entro 500 metri dal luogo concordato.')).toBeTruthy();
    expect(server.paths().some((path) => path.endsWith('/open')), 'nothing sent from outside the place').toBe(false);

    where = HOME;
    await user.click(screen.getByRole('button', { name: 'Apri' }));
    await waitFor(() => expect(server.paths().some((path) => path.endsWith('/open'))).toBe(true));
    expect(await server.requests.find((request) => request.url.endsWith('/open'))?.json()).toEqual({ localTime: '10:00', position: HOME });
  });

  it('explains how to give the position when it is denied', async () =>
  {
    const place = constraint(patient.id, { type: 'LOCATION', scope: 'READ', latitude: 45, longitude: 9, radiusMetres: 500, direction: 'WITHIN' });
    geolocation((_, ko) => ko({ code: 1, PERMISSION_DENIED: 1 } as GeolocationPositionError));
    const { user } = renderDiscussion(patient, conversationWith([envelope()]), [place]);

    await user.click(await screen.findByRole('button', { name: 'Apri' }));

    expect(await screen.findByText(POSITION_DENIED)).toBeTruthy();
  });
});

describe('the status line', () =>
{
  it('says the turn is the other partner after sending', async () =>
  {
    renderDiscussion(partner, conversationWith([samAsks()]));
    expect(await screen.findByText('Hai inviato l’ultimo messaggio: ora tocca ad Alex.')).toBeTruthy();
  });

  it('says when the wait ends, and changes by itself when it does', async () =>
  {
    // Opened 1h59m59s ago, with a wait of 2 hours: one second to go.
    const opened = samAsks({ readAt: new Date(Date.now() - 2 * 3_600_000 + 1000).toISOString() });
    renderDiscussion(patient, conversationWith([opened]), [constraint(patient.id, { type: 'REPLY_DELAY', delay: 'PT2H' })]);

    expect(await screen.findByText('Potrai rispondere dalle 10:00 del 16 novembre 2026.')).toBeTruthy();
    vi.setSystemTime(new Date(Date.now() + 2000));

    expect(await screen.findByText('Tocca a te: puoi rispondere.', {}, { timeout: 3000 })).toBeTruthy();
  });

  it('says from when one can write, and where', async () =>
  {
    const opened = samAsks({ readAt: '2026-11-16T08:00:00Z' });
    renderDiscussion(patient, conversationWith([opened]), [constraint(patient.id, { type: 'TIME', notBefore: '18:00' })]);
    expect(await screen.findByText('Potrai scrivere dalle 18:00.')).toBeTruthy();
  });

  it('says that the position will be checked when the writing starts', async () =>
  {
    const opened = samAsks({ readAt: '2026-11-16T08:00:00Z' });
    renderDiscussion(patient, conversationWith([opened]), [constraint(patient.id, { type: 'LOCATION', scope: 'WRITE', radiusMetres: 2000, direction: 'BEYOND', latitude: 45, longitude: 9 })]);
    expect(await screen.findByText('Per scrivere dovrai trovarti a più di 2 km dal luogo concordato: l’app lo verificherà quando inizierai a scrivere.')).toBeTruthy();
  });

  it('shows the therapist whose turn it is', async () =>
  {
    renderDiscussion(therapist, conversationWith([samAsks()]));
    expect(await screen.findByText('Tocca ad Alex.')).toBeTruthy();
  });

  it('says that a discussion is read-only once closed or once the couple ended', async () =>
  {
    const open = conversationWith([samAsks({ readAt: '2026-11-16T08:00:00Z' })]);
    renderDiscussion(patient, { ...open, discussions: open.discussions.map((discussion) => ({ ...discussion, closed: true })) });
    expect(await screen.findByText('Questo discorso è chiuso: si può solo consultare.')).toBeTruthy();
  });

  it('invites the first message of a discussion', async () =>
  {
    renderDiscussion(patient, conversationWith([]));
    expect(await screen.findByText('Puoi scrivere il primo messaggio.')).toBeTruthy();
  });
});

describe('the ways to a conversation', () =>
{
  it('lead from the "Da fare" to the discussion of the messages to open', async () =>
  {
    renderApp({
      account: patient,
      answer: (_, path) =>
      {
        if (path === 'GET /api/me/summary')
        {
          return {
            ...emptySummary,
            couples: [{
              couple: talkingCouple.id, conversation: CONVERSATION, negotiationsWaitingForYou: [], unopenedMessages: [{ discussion: 2, count: 1 }],
              closingRequestsToAnswer: [], therapistRequestsToDecide: [], outcomesToDeclare: [], constraintRequestsToDecide: [],
            }],
          };
        }

        return path === 'GET /api/me/couples' ? [talkingCouple] : undefined;
      },
    });

    expect(await screen.findByRole('link', { name: 'Discorso 2: 1 messaggio da aprire.' })).toHaveProperty('pathname', `/conversazioni/${CONVERSATION}/discorsi/2`);
    expect(screen.getByRole('link', { name: 'Apri la conversazione' })).toHaveProperty('pathname', `/conversazioni/${CONVERSATION}`);
  });

  it('lead from a stipula to the paragraph its discussion expands', async () =>
  {
    const stipula: Negotiation = {
      id: '0190a7e2-0000-7000-8000-0000000000f1', coupleId: talkingCouple.id, purpose: 'OPENED_DISCUSSION', originParagraphId: question.id,
      status: 'DRAFT', currentResponder: partner.id, sentProposals: [], draft: null, startedAt: '2026-11-16T09:00:00Z', closedAt: null, closedBy: null,
      request: { id: '0190a7e2-0000-7000-8000-0000000000b0', author: patient.id, kind: 'DISCUSSION_TITLE', text: null, title: 'Come stai davvero', subtitle: 'Da capo', justification: null, sentAt: null, status: 'DRAFT' },
    };
    renderApp({
      path: `/stipule/${stipula.id}`,
      account: partner,
      answer: (_, path) =>
      {
        if (path === `GET /api/negotiations/${stipula.id}`)
        {
          return stipula;
        }

        if (path === `GET /api/conversations/${CONVERSATION}`)
        {
          return conversationWith([samAsks()]);
        }

        return path === 'GET /api/me/couples' ? [talkingCouple] : undefined;
      },
    });

    expect((await screen.findByRole('link', { name: '«Come stai?»' })).getAttribute('href')).toBe(`/conversazioni/${CONVERSATION}/discorsi/0#p-${question.id}`);
  });

  it('do not lead to a discussion that does not exist', async () =>
  {
    renderApp({
      path: `/conversazioni/${CONVERSATION}/discorsi/9`,
      account: patient,
      answer: (_, path) =>
      {
        if (path === `GET /api/conversations/${CONVERSATION}`)
        {
          return conversationWith([]);
        }

        return path.endsWith('/constraints') ? { inForce: [], requests: [] } : path === 'GET /api/me/couples' ? [talkingCouple] : undefined;
      },
    });

    expect(await screen.findByRole('heading', { name: 'Pagina non trovata' })).toBeTruthy();
  });
});
