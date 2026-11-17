import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Account, ArchiveEntry, ArchiveSearchResult, Conversation, NegotiationHistory, Proposal } from '../api/client';
import { CONVERSATION, conversationWith, endedCouple, message, paragraph, partner, patient, problem, talkingCouple, therapist } from '../api/testing';
import { renderApp } from '../app/testing';
import { forgetSearch } from './archive';
import { NO_ARCHIVE, NOTHING_IN_PERIOD, RULE_TEXTS, UNOPENED_LEFT_OUT } from './ArchivePages';

const ARCHIVE = `/api/conversations/${CONVERSATION}/archive`;
const question = paragraph(1, 'Perché le vacanze al mare?', { type: 'QUESTION' });
const answer = paragraph(2, 'Perché amo il mare', { type: 'POINTED', references: [question.id] });

function exchange(): Conversation
{
  const conversation = conversationWith([
    message(1, patient.id, [question]),
    message(2, partner.id, [answer], { readAt: '2026-11-16T18:00:00Z' }),
  ]);
  conversation.discussions.push({ id: 1, title: 'Le vacanze', subtitle: 'Dove', closed: false, originParagraphId: null, lastSender: null, messages: [] });

  return conversation;
}

function entryOf(item: typeof question, messageNumber: number, sender: string, extra: Partial<ArchiveEntry> = {}): ArchiveEntry
{
  return {
    paragraphId: item.id,
    type: item.type,
    title: item.title,
    subtitle: item.subtitle,
    body: item.body,
    discussionId: 0,
    messageId: message(messageNumber, sender, []).id,
    sender,
    sentAt: '2026-11-16T17:30:00Z',
    closedEnvelope: false,
    ...extra,
  };
}

function envelope(messageNumber: number, sender: string): ArchiveEntry
{
  return {
    paragraphId: null, type: null, title: null, subtitle: null, body: null, discussionId: 0,
    messageId: message(messageNumber, sender, []).id, sender, sentAt: '2026-11-16T19:00:00Z', closedEnvelope: true,
  };
}

function renderArchive(path: string, answer: (request: Request, path: string) => unknown, account: Account = patient)
{
  return renderApp({
    path,
    account,
    answer: (request, requested) =>
    {
      const given = answer(request, requested);

      if (given !== undefined)
      {
        return given;
      }

      if (requested === `GET /api/conversations/${CONVERSATION}`)
      {
        return exchange();
      }

      return requested === 'GET /api/me/couples' ? [talkingCouple] : undefined;
    },
  });
}

beforeEach(() =>
{
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-17T09:00:00Z'));
});

afterEach(() =>
{
  vi.useRealTimers();
  forgetSearch();
});

describe('the search', () =>
{
  const found: ArchiveSearchResult = {
    paragraphs: [entryOf(answer, 2, partner.id)],
    proposals: [{
      negotiationId: 'n1',
      purpose: 'STARTED_CONVERSATION',
      section: 'TOOK_EFFECT',
      proposal: { id: 'pr1', kind: 'AGREEMENT', author: patient.id, text: 'Il mare ci aiuta a parlare.', title: null, subtitle: null, justification: 'Così.', sentAt: '2026-11-10T10:00:00Z', status: 'ACCEPTED' } as Proposal,
    }],
  };

  it('sends the words in the body, never in the address, and marks what it found', async () =>
  {
    const { user, server, router } = renderArchive(`/conversazioni/${CONVERSATION}/archivio`, (_, path) => (path === `POST ${ARCHIVE}/search` ? found : undefined));
    expect(await screen.findByText(UNOPENED_LEFT_OUT)).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Cerca' }));
    expect(await screen.findByText('Scrivi cosa cercare.')).toBeTruthy();
    expect(server.paths()).not.toContain(`POST ${ARCHIVE}/search`);

    await user.type(screen.getByLabelText('Cerca una parola o una frase'), 'MARE');
    await user.click(screen.getByRole('button', { name: 'Cerca' }));
    await screen.findByRole('heading', { name: 'Nei messaggi (1)' });
    expect(await server.requests.find((sent) => sent.url.endsWith('/search'))?.json()).toEqual({ text: 'MARE' });
    expect(JSON.stringify(router.state.location)).not.toMatch(/mare/i);

    // The paragraph's title and text, and the agreement text found.
    const marks = document.querySelectorAll('mark');
    expect([...marks].map((mark) => mark.textContent)).toEqual(['mare', 'mare', 'mare']);
    const result = screen.getByRole('article', { name: /^Messaggio: Discorso principale, Sam, / });
    expect(within(result).getByText('Risposta puntuale')).toBeTruthy();
    expect(within(result).getByText('Risponde a «Perché le vacanze al mare?»')).toBeTruthy();
    expect(within(result).getByRole('link', { name: 'Vai al paragrafo' })).toHaveProperty('hash', `#p-${answer.id}`);
    expect(screen.getByRole('heading', { name: 'Nelle stipule (1)' })).toBeTruthy();
    expect(screen.getByText('Stipula dell’accordo · Con effetto · la tua proposta del 10 novembre 2026')).toBeTruthy();
  });

  it('is found again coming back from a topic history, from memory only', async () =>
  {
    const topic = { paragraphId: answer.id, rule: 'MAIN_CONVERSATION', entries: [entryOf(answer, 2, partner.id)] };
    const { user, router } = renderArchive(`/conversazioni/${CONVERSATION}/archivio`,
      (_, path) => (path === `POST ${ARCHIVE}/search` ? found : path === `GET ${ARCHIVE}/topics/${answer.id}` ? topic : undefined));
    await user.type(await screen.findByLabelText('Cerca una parola o una frase'), 'mare');
    await user.click(screen.getByRole('button', { name: 'Cerca' }));
    await user.click(await screen.findByRole('link', { name: 'Storia dell’argomento' }));
    expect(await screen.findByRole('heading', { name: 'Storia dell’argomento' })).toBeTruthy();

    await router.navigate(-1);
    expect(await screen.findByRole('heading', { name: 'Nei messaggi (1)' })).toBeTruthy();
    expect(screen.getByLabelText('Cerca una parola o una frase')).toHaveProperty('value', 'mare');
    expect(JSON.stringify(router.state.location)).not.toMatch(/mare/i);
  });

  it('says when it finds nothing; the therapist isn’t told about unopened messages', async () =>
  {
    const { user } = renderArchive(`/conversazioni/${CONVERSATION}/archivio`,
      (_, path) => (path === `POST ${ARCHIVE}/search` ? { paragraphs: [], proposals: [] } : undefined), therapist);
    await user.type(await screen.findByLabelText('Cerca una parola o una frase'), 'montagna');
    await user.click(screen.getByRole('button', { name: 'Cerca' }));

    expect(await screen.findByText('Nessun risultato per «montagna».')).toBeTruthy();
    expect(screen.queryByText(UNOPENED_LEFT_OUT)).toBeNull();
  });
});

describe('the period', () =>
{
  it('shows the last seven days by default, an unopened message as one envelope', async () =>
  {
    const { server } = renderArchive(`/conversazioni/${CONVERSATION}/archivio/periodo`,
      (_, path) => (path === `GET ${ARCHIVE}/messages` ? [entryOf(question, 1, patient.id), envelope(3, partner.id)] : undefined));

    expect(await screen.findByText('Discorso principale · Tu · 16 novembre 2026, 18:30')).toBeTruthy();
    const query = new URL(server.requests.find((sent) => sent.url.includes('/messages'))?.url ?? '').searchParams;
    expect([query.get('from'), query.get('to')]).toEqual(['2026-11-10T23:00:00.000Z', '2026-11-17T23:00:00.000Z']);
    expect(screen.getByLabelText('Dal')).toHaveProperty('value', '2026-11-11');
    expect(screen.getByLabelText('Al')).toHaveProperty('value', '2026-11-17');

    const closed = screen.getByRole('article', { name: 'Messaggio di Sam, da aprire' });
    expect(within(closed).getByText('Discorso principale · Messaggio di Sam · 16 novembre 2026, 20:00 · da aprire')).toBeTruthy();
    expect(within(closed).getByRole('link', { name: 'Aprilo nel discorso' })).toHaveProperty('pathname', `/conversazioni/${CONVERSATION}/discorsi/0`);
  });

  it('puts the days in the address, after checking them', async () =>
  {
    const { user, router, server } = renderArchive(`/conversazioni/${CONVERSATION}/archivio/periodo?dal=2026-11-01&al=2026-11-02`,
      (_, path) => (path === `GET ${ARCHIVE}/messages` ? [] : undefined));
    expect(await screen.findByText(NOTHING_IN_PERIOD)).toBeTruthy();

    await user.clear(screen.getByLabelText('Dal'));
    await user.type(screen.getByLabelText('Dal'), '2026-11-05');
    await user.click(screen.getByRole('button', { name: 'Mostra' }));
    expect(await screen.findByText('La data di inizio viene dopo quella di fine.')).toBeTruthy();
    expect(router.state.location.search).toBe('?dal=2026-11-01&al=2026-11-02');

    await user.clear(screen.getByLabelText('Al'));
    await user.type(screen.getByLabelText('Al'), '2026-11-06');
    await user.click(screen.getByRole('button', { name: 'Mostra' }));
    await waitFor(() => expect(router.state.location.search).toBe('?dal=2026-11-05&al=2026-11-06'));
    await waitFor(() => expect(server.requests.filter((sent) => sent.url.includes('/messages'))).toHaveLength(2));
  });

  it('asks nothing of the server for days that don’t exist in the address', async () =>
  {
    const { server } = renderArchive(`/conversazioni/${CONVERSATION}/archivio/periodo?dal=2026-02-30&al=2026-03-01`, () => undefined);

    expect(await screen.findByText('Scegli le due date.')).toBeTruthy();
    expect(server.paths().some((path) => path.endsWith('/messages'))).toBe(false);
  });
});

describe('the questions without an answer', () =>
{
  it('separate those skipped for good from those still waiting', async () =>
  {
    const skipped = entryOf(question, 1, patient.id);
    const later = paragraph(5, 'E la montagna?', { type: 'QUESTION' });
    renderArchive(`/conversazioni/${CONVERSATION}/archivio/domande`, (_, path) => (path === `GET ${ARCHIVE}/unanswered-questions`
      ? { stillOpen: [skipped, entryOf(later, 3, partner.id)], leftUnanswered: [skipped] }
      : undefined));

    const skippedSection = (await screen.findByRole('heading', { name: 'Lasciate senza risposta' })).parentElement as HTMLElement;
    expect(within(skippedSection).getByText('Perché le vacanze al mare?')).toBeTruthy();
    expect(within(skippedSection).queryByText('E la montagna?')).toBeNull();
    const waitingSection = screen.getByRole('heading', { name: 'In attesa di risposta' }).parentElement as HTMLElement;
    expect(within(waitingSection).getByText('E la montagna?')).toBeTruthy();
    expect(within(waitingSection).queryByText('Perché le vacanze al mare?')).toBeNull();
  });
});

describe('the concluded stipule', () =>
{
  const proposal = (id: string, extra: Partial<Proposal> = {}): Proposal => ({
    id, kind: 'AGREEMENT', author: patient.id, text: 'Ci scriviamo con calma.', title: null, subtitle: null, justification: 'Va bene così.',
    sentAt: '2026-11-10T10:00:00Z', status: 'ACCEPTED', ...extra,
  });
  const history: NegotiationHistory = {
    tookEffect: [
      { negotiationId: 'n1', purpose: 'STARTED_CONVERSATION', discussionId: 0, archivedAt: '2026-11-10T10:05:00Z', proposals: [proposal('a')] },
      {
        negotiationId: 'n2', purpose: 'OPENED_DISCUSSION', discussionId: 1, archivedAt: '2026-11-12T10:05:00Z',
        proposals: [proposal('b', { kind: 'DISCUSSION_TITLE', text: null, title: 'Le vacanze', subtitle: 'Dove' })],
      },
    ],
    failed: [{
      negotiationId: 'n3', attemptedPurpose: 'MODIFIED_AGREEMENT', outcome: 'REFUSED', closedBy: partner.id, startedAt: '2026-11-13T10:00:00Z',
      closedAt: '2026-11-14T10:00:00Z', proposals: [proposal('c', { status: 'WAITING_FOR_RESPONSE', text: 'Ci scriviamo ogni giorno.' })],
    }],
  };

  it('show those that took effect, with the discussion they opened, and those that failed, by whom', async () =>
  {
    const { user } = renderArchive(`/conversazioni/${CONVERSATION}/archivio/stipule`, (_, path) => (path === `GET ${ARCHIVE}/negotiations` ? history : undefined));

    const opened = await screen.findByRole('article', { name: 'Richiesta di un discorso specifico del 12 novembre 2026' });
    expect(within(opened).getByRole('link', { name: '«Le vacanze»' })).toHaveProperty('pathname', `/conversazioni/${CONVERSATION}/discorsi/1`);
    const failed = screen.getByRole('article', { name: 'Modifica dell’accordo non riuscita' });
    expect(within(failed).getByText(/Modifica dell’accordo · Rifiutata da Sam il 14 novembre 2026 · iniziata il 13 novembre 2026/)).toBeTruthy();

    // The proposals wait behind a "Mostra", the last one highlighted as in the stipula's page.
    const shown = within(failed).getByText('Mostra le proposte (1)');
    const details = shown.parentElement as HTMLDetailsElement;
    expect(details.open).toBe(false);
    await user.click(shown);
    expect(details.open).toBe(true);
    expect(within(details).getByText('Ci scriviamo ogni giorno.').closest('[aria-current="true"]')).toBeTruthy();
  });
});

describe('the topic history', () =>
{
  it('says its rule, highlights where it starts and keeps envelopes in place', async () =>
  {
    renderArchive(`/conversazioni/${CONVERSATION}/archivio/argomenti/${answer.id}`, (_, path) => (path === `GET ${ARCHIVE}/topics/${answer.id}`
      ? { paragraphId: answer.id, rule: 'SPECIFIC_DISCUSSION', entries: [entryOf(question, 1, patient.id), entryOf(answer, 2, partner.id), envelope(3, partner.id)] }
      : undefined));

    expect(await screen.findByText('Parte da «Perché amo il mare»')).toBeTruthy();
    expect(screen.getByText(RULE_TEXTS.SPECIFIC_DISCUSSION)).toBeTruthy();
    const start = screen.getByRole('region', { name: 'Perché amo il mare' });
    expect(start.getAttribute('aria-current')).toBe('true');
    expect(within(start).queryByRole('link', { name: 'Storia dell’argomento' }), 'it is the page already open').toBeNull();
    expect(within(screen.getByRole('region', { name: 'Perché le vacanze al mare?' })).getByRole('link', { name: 'Storia dell’argomento' })).toBeTruthy();
    expect(screen.getByRole('article', { name: 'Messaggio di Sam, da aprire' })).toBeTruthy();
  });

  it('of a paragraph that doesn’t exist, or isn’t opened, isn’t found', async () =>
  {
    renderArchive(`/conversazioni/${CONVERSATION}/archivio/argomenti/nessuno`, (_, path) => (path.includes('/topics/') ? problem(404, 'NOT_FOUND') : undefined));

    expect(await screen.findByRole('heading', { name: 'Pagina non trovata' })).toBeTruthy();
  });
});

describe('the ways to the archive', () =>
{
  it('are the bar, the conversation page and every paragraph of the letters', async () =>
  {
    const first = renderArchive('/archivio', () => undefined);
    await waitFor(() => expect(first.router.state.location.pathname).toBe(`/conversazioni/${CONVERSATION}/archivio`));
    expect(screen.getByRole('link', { name: 'Archivio' })).toHaveProperty('pathname', '/archivio');

    first.router.dispose();
    document.body.innerHTML = '';
    // A patient with only an ended couple finds its archive; with none, a text.
    const ended = renderApp({ path: '/archivio', account: patient, answer: (_, path) => (path === 'GET /api/me/couples' ? [{ ...endedCouple, conversation: CONVERSATION }] : undefined) });
    await waitFor(() => expect(ended.router.state.location.pathname).toBe(`/conversazioni/${CONVERSATION}/archivio`));
    ended.router.dispose();
    document.body.innerHTML = '';
    renderApp({ path: '/archivio', account: patient, answer: (_, path) => (path === 'GET /api/me/couples' ? [] : undefined) });
    expect(await screen.findByText(NO_ARCHIVE)).toBeTruthy();

    document.body.innerHTML = '';
    renderArchive(`/conversazioni/${CONVERSATION}`, () => undefined, therapist);
    expect(await screen.findByRole('link', { name: 'Archivio' })).toHaveProperty('pathname', `/conversazioni/${CONVERSATION}/archivio`);

    document.body.innerHTML = '';
    renderArchive(`/conversazioni/${CONVERSATION}/discorsi/0`, (_, path) => (path.endsWith('/constraints') ? { inForce: [], requests: [] } : undefined));
    const said = await screen.findByRole('region', { name: 'Perché amo il mare' });
    expect(within(said).getByRole('link', { name: 'Storia dell’argomento' })).toHaveProperty('pathname', `/conversazioni/${CONVERSATION}/archivio/argomenti/${answer.id}`);
  });
});
