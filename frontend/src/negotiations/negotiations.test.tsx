import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import type { Couple, Negotiation, Proposal } from '../api/client';
import { activeCouple, noContent, partner, patient, problem, therapist } from '../api/testing';
import { renderApp } from '../app/testing';
import { discardUnsentText } from '../session/unsent';
import { ERROR_TEXTS } from '../texts/errors';
import { AGREEMENT_HINT, FROM_A_PARAGRAPH, REQUEST_SENT } from './NewNegotiationPages';
import { DRAFT_SAVED } from './NegotiationPage';
import { ACCEPT_TEXTS, ACCEPTED_NOTICES, ENDED_WITHOUT_AGREEMENT, phaseOf, REFUSE_REQUEST_TEXT, WITHDRAW_TEXT } from './phase';

const ID = '0190a7e2-0000-7000-8000-0000000000f1';
const TEXT = 'Ci scriviamo con calma, una lettera alla volta.';

const request: Proposal = {
  id: '0190a7e2-0000-7000-8000-0000000000b0',
  author: patient.id,
  kind: 'AGREEMENT',
  text: TEXT,
  title: null,
  subtitle: null,
  justification: null,
  sentAt: null,
  status: 'DRAFT',
};

function sent(author: string, text: string, status: Proposal['status'], index: number): Proposal
{
  return { ...request, id: `0190a7e2-0000-7000-8000-0000000000b${index}`, author, text, justification: `Perché ${index}`, sentAt: '2026-11-17T09:00:00Z', status };
}

function stipula(overrides: Partial<Negotiation> = {}): Negotiation
{
  return {
    id: ID,
    coupleId: activeCouple.id,
    purpose: 'STARTED_CONVERSATION',
    originParagraphId: null,
    status: 'DRAFT',
    currentResponder: partner.id,
    request,
    sentProposals: [],
    draft: null,
    startedAt: '2026-11-16T09:00:00Z',
    closedAt: null,
    closedBy: null,
    ...overrides,
  };
}

/** The stipula page on a fake server: the negotiation given (changing it changes the next reads), the couple. */
function renderStipula(account = partner, first = stipula(), more: (request: Request, path: string) => unknown = () => undefined)
{
  const state = { negotiation: first };
  const rendered = renderApp({
    path: `/stipule/${ID}`,
    account,
    answer: (request, path) =>
    {
      const given = more(request, path);

      if (given !== undefined)
      {
        return given;
      }

      if (path === `GET /api/negotiations/${ID}`)
      {
        return state.negotiation;
      }

      return path === 'GET /api/me/couples' ? [activeCouple] : undefined;
    },
  });

  return { ...rendered, state };
}

afterEach(() => discardUnsentText());

describe('where a stipula stands', () =>
{
  it('depends on its status, on whose turn it is and on the draft', () =>
  {
    const waiting = sent(patient.id, TEXT, 'WAITING_FOR_RESPONSE', 1);

    expect(phaseOf(stipula(), partner)).toBe('answer-request');
    expect(phaseOf(stipula(), patient)).toBe('await-request');
    expect(phaseOf(stipula(), therapist)).toBe('await-request');
    expect(phaseOf(stipula({ status: 'ACCEPTED', currentResponder: patient.id, draft: { ...request, status: 'DRAFT' } }), patient)).toBe('write');
    expect(phaseOf(stipula({ status: 'ACCEPTED', currentResponder: partner.id, sentProposals: [waiting] }), partner)).toBe('answer-proposal');
    expect(phaseOf(stipula({ status: 'ACCEPTED', currentResponder: partner.id, sentProposals: [waiting] }), patient)).toBe('await');
    expect(phaseOf(stipula({ status: 'ACCEPTED', currentResponder: partner.id, sentProposals: [waiting] }), therapist)).toBe('await');
    expect(phaseOf(stipula({ status: 'REFUSED' }), patient)).toBe('closed');
    expect(phaseOf(stipula({ status: 'ABANDONED' }), partner)).toBe('closed');
  });
});

describe('the stipula page', () =>
{
  it('asks the other partner to accept the request, and goes on', async () =>
  {
    const { user, server, state } = renderStipula(partner, stipula(), (_, path) =>
    {
      if (path.endsWith('/accept-request'))
      {
        state.negotiation = stipula({ status: 'ACCEPTED', currentResponder: patient.id });

        return noContent();
      }

      return undefined;
    });

    expect(await screen.findByText('Alex chiede di avviare questa stipula. Vuoi discuterne?')).toBeTruthy();
    expect(screen.getByText(TEXT)).toBeTruthy();
    expect(screen.getByText('Richiesta da Alex il 16 novembre 2026 · Richiesta in attesa di risposta')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Accetta la richiesta' }));

    expect(await screen.findByText('Tocca ad Alex.')).toBeTruthy();
    expect(server.paths()).toContain(`POST /api/negotiations/${ID}/accept-request`);
  });

  it('refuses the request only after a confirmation, then goes to the couple', async () =>
  {
    const { user, server, router } = renderStipula(partner, stipula(), (_, path) => (path.endsWith('/refuse-request') ? noContent() : undefined));

    await user.click(await screen.findByRole('button', { name: 'Rifiuta la richiesta' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(REFUSE_REQUEST_TEXT)).toBeTruthy();
    await user.click(within(dialog).getByRole('button', { name: 'Rifiuta la richiesta' }));

    expect(await screen.findByText(ENDED_WITHOUT_AGREEMENT)).toBeTruthy();
    expect(router.state.location.pathname).toBe(`/coppie/${activeCouple.id}`);
    expect(server.paths()).toContain(`POST /api/negotiations/${ID}/refuse-request`);
  });

  it('lets the requester withdraw the request while waiting', async () =>
  {
    const { user, server } = renderStipula(patient, stipula(), (_, path) => (path.endsWith('/abandon') ? noContent() : undefined));

    expect(await screen.findByText('In attesa della risposta di Sam.')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Ritira la richiesta' }));
    expect(within(await screen.findByRole('dialog')).getByText(WITHDRAW_TEXT)).toBeTruthy();
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Ritira la richiesta' }));

    await waitFor(() => expect(server.paths()).toContain(`POST /api/negotiations/${ID}/abandon`));
  });

  it('shows the therapist everything but the drafts, with no action', async () =>
  {
    const waiting = sent(patient.id, 'Una proposta inviata.', 'WAITING_FOR_RESPONSE', 1);
    renderStipula(therapist, stipula({ status: 'ACCEPTED', currentResponder: partner.id, sentProposals: [waiting] }));

    expect(await screen.findByText('Tocca a Sam.')).toBeTruthy();
    expect(screen.getByText('Una proposta inviata.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Accetta|Rifiuta|Ritira|Abbandona|Invia|Salva/ })).toBeNull();
    expect(screen.queryByLabelText('Motivazione')).toBeNull();
  });

  it('lets a partner review the draft and send it with a justification', async () =>
  {
    const { user, server } = renderStipula(
      patient,
      stipula({ status: 'ACCEPTED', currentResponder: patient.id, draft: { ...request } }),
      (_, path) => (path.endsWith('/send') || path.endsWith('/draft') ? noContent() : undefined),
    );

    expect(await screen.findByText('Tocca a te: rivedi il testo e invialo con una motivazione.')).toBeTruthy();
    expect(screen.getByLabelText('Testo dell’accordo')).toHaveProperty('value', TEXT);
    await user.click(screen.getByRole('button', { name: 'Invia la proposta' }));
    expect(await screen.findByText('Manca la motivazione.')).toBeTruthy();

    await user.type(screen.getByLabelText('Motivazione'), 'Così ci diamo il tempo di pensare.');
    await user.click(screen.getByRole('button', { name: 'Invia la proposta' }));

    await waitFor(() => expect(server.paths()).toContain(`POST /api/negotiations/${ID}/send`));
    expect(server.paths(), 'the text was not changed: nothing to save').not.toContain(`PUT /api/negotiations/${ID}/draft`);
    expect(await server.requests.find((sentRequest) => sentRequest.url.endsWith('/send'))?.json()).toEqual({ justification: 'Così ci diamo il tempo di pensare.' });
  });

  it('saves the changed text of the draft, also before sending it', async () =>
  {
    const { user, server } = renderStipula(
      patient,
      stipula({ status: 'ACCEPTED', currentResponder: patient.id, draft: { ...request } }),
      (_, path) => (path.endsWith('/draft') ? noContent() : undefined),
    );

    const field = await screen.findByLabelText('Testo dell’accordo');
    await user.type(field, ' E ci rileggiamo.');
    expect(screen.getByText(`${TEXT.length + 17}/4000`)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Salva la bozza' }));

    expect(await screen.findByText(DRAFT_SAVED)).toBeTruthy();
    expect(await server.requests.find((sentRequest) => sentRequest.method === 'PUT')?.json()).toEqual({ text: `${TEXT} E ci rileggiamo.` });
  });

  it('accepts a proposal after saying what happens, then goes to the couple', async () =>
  {
    const waiting = sent(patient.id, 'Il testo finale.', 'WAITING_FOR_RESPONSE', 1);
    const { user, server, router } = renderStipula(
      partner,
      stipula({ status: 'ACCEPTED', currentResponder: partner.id, sentProposals: [sent(patient.id, TEXT, 'REFUSED', 2), waiting] }),
      (_, path) => (path.endsWith('/accept-proposal') ? { conversationId: activeCouple.id, openedDiscussion: 0 } : undefined),
    );

    expect(await screen.findByText('Alex ti ha inviato una proposta.')).toBeTruthy();
    const proposals = screen.getAllByText(/^Proposta di Alex/);
    expect(proposals).toHaveLength(2);
    expect(proposals[1]?.closest('[aria-current="true"]'), 'the last proposal is highlighted').toBeTruthy();
    expect(screen.getByText('Perché 1')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Accetta la proposta' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(ACCEPT_TEXTS.STARTED_CONVERSATION)).toBeTruthy();
    await user.click(within(dialog).getByRole('button', { name: 'Accetta la proposta' }));

    expect(await screen.findByText(ACCEPTED_NOTICES.STARTED_CONVERSATION)).toBeTruthy();
    expect(router.state.location.pathname).toBe(`/coppie/${activeCouple.id}`);
    expect(server.paths()).toContain(`POST /api/negotiations/${ID}/accept-proposal`);
  });

  it('refuses a proposal by opening an editable copy of it', async () =>
  {
    const waiting = sent(patient.id, 'Da rivedere.', 'WAITING_FOR_RESPONSE', 1);
    const { user, state } = renderStipula(partner, stipula({ status: 'ACCEPTED', currentResponder: partner.id, sentProposals: [waiting] }), (_, path) =>
    {
      if (path.endsWith('/refuse-proposal'))
      {
        state.negotiation = stipula({
          status: 'ACCEPTED',
          currentResponder: partner.id,
          sentProposals: [{ ...waiting, status: 'REFUSED' }],
          draft: { ...waiting, id: '0190a7e2-0000-7000-8000-0000000000c9', author: partner.id, status: 'DRAFT', justification: null, sentAt: null },
        });

        return Response.json({ id: '0190a7e2-0000-7000-8000-0000000000c9' }, { status: 201 });
      }

      return undefined;
    });

    await user.click(await screen.findByRole('button', { name: 'Rifiuta e proponi una modifica' }));

    expect(await screen.findByLabelText('Testo dell’accordo')).toHaveProperty('value', 'Da rivedere.');
    expect(screen.getByText('Rifiutata', { exact: false })).toBeTruthy();
  });

  it('keeps what was written when the stipula ended meanwhile', async () =>
  {
    const { user, server } = renderStipula(
      patient,
      stipula({ status: 'ACCEPTED', currentResponder: patient.id, draft: { ...request } }),
      (_, path) => (path.endsWith('/send') ? problem(409, 'NEGOTIATION_ENDED') : undefined),
    );

    await user.type(await screen.findByLabelText('Motivazione'), 'Una motivazione lunga e pensata.');
    await user.click(screen.getByRole('button', { name: 'Invia la proposta' }));

    expect(await screen.findByText(ERROR_TEXTS.NEGOTIATION_ENDED)).toBeTruthy();
    expect(screen.getByLabelText('Motivazione')).toHaveProperty('value', 'Una motivazione lunga e pensata.');
    expect(server.paths().filter((path) => path === `GET /api/negotiations/${ID}`), 'not re-read while the form holds text').toHaveLength(1);
  });

  it('says how a stipula ended', async () =>
  {
    renderStipula(patient, stipula({ status: 'REFUSED', closedBy: partner.id, closedAt: '2026-11-17T10:00:00Z' }));

    expect(await screen.findByText('Richiesta rifiutata da Sam il 17 novembre 2026.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Accetta|Rifiuta|Ritira|Abbandona/ })).toBeNull();
  });

  it('shows the title and subtitle of a discussion, and where it comes from', async () =>
  {
    const discussion: Proposal = { ...request, kind: 'DISCUSSION_TITLE', text: null, title: 'Le vacanze', subtitle: 'Dove e con chi' };
    renderStipula(partner, stipula({ purpose: 'OPENED_DISCUSSION', request: discussion, originParagraphId: request.id }));

    expect(await screen.findByRole('heading', { name: 'Richiesta di un discorso specifico' })).toBeTruthy();
    expect(screen.getByText('Le vacanze')).toBeTruthy();
    expect(screen.getByText('Dove e con chi')).toBeTruthy();
    expect(screen.getByText(FROM_A_PARAGRAPH)).toBeTruthy();
  });
});

describe('the couple page and the start of a stipula', () =>
{
  const withConversation: Couple = { ...activeCouple, conversation: '0190a7e2-0000-7000-8000-0000000000d1' };

  function renderCouple(account = patient, couple: Couple = activeCouple, negotiations: Negotiation[] = [], failedStarts: Negotiation[] = [])
  {
    return renderApp({
      path: `/coppie/${couple.id}`,
      account,
      answer: (_, path) =>
      {
        if (path === 'GET /api/me/couples')
        {
          return [couple];
        }

        if (path.endsWith('/negotiations'))
        {
          return negotiations;
        }

        return path.endsWith('/failed-starts') ? failedStarts : undefined;
      },
    });
  }

  it('lists the stipule in progress, each a link, with whose turn it is', async () =>
  {
    renderCouple(partner, activeCouple, [stipula()]);

    const link = await screen.findByRole('link', { name: 'Stipula dell’accordo' });
    expect(link).toHaveProperty('pathname', `/stipule/${ID}`);
    expect(link.parentElement?.textContent).toBe('Stipula dell’accordo · richiesta da Alex il 16 novembre 2026 · tocca a te');
  });

  it('offers to propose the agreement, but not twice, and nothing to the therapist', async () =>
  {
    renderCouple();
    expect(await screen.findByRole('link', { name: 'Proponi l’accordo' })).toHaveProperty('pathname', `/coppie/${activeCouple.id}/nuovo-accordo`);
    expect(screen.queryByRole('link', { name: 'Proponi un discorso specifico' })).toBeNull();
  });

  it('hides the agreement button while an agreement stipula is in progress', async () =>
  {
    renderCouple(patient, activeCouple, [stipula()]);
    await screen.findByRole('link', { name: 'Stipula dell’accordo' });
    expect(screen.queryByRole('link', { name: 'Proponi l’accordo' })).toBeNull();
  });

  it('offers a change of the agreement and a specific discussion once the conversation exists', async () =>
  {
    renderCouple(patient, withConversation);

    expect(await screen.findByRole('link', { name: 'Proponi una modifica dell’accordo' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Proponi un discorso specifico' })).toHaveProperty('pathname', `/conversazioni/${withConversation.conversation}/nuovo-discorso`);
  });

  it('offers no stipula to the therapist', async () =>
  {
    renderCouple(therapist, withConversation);

    expect(await screen.findByText('Nessuna stipula in corso.')).toBeTruthy();
    expect(screen.queryByRole('link', { name: /^Proponi/ })).toBeNull();
  });

  it('lists the failed attempts to start the conversation', async () =>
  {
    renderCouple(patient, activeCouple, [], [stipula({ status: 'REFUSED', closedBy: partner.id, closedAt: '2026-11-17T10:00:00Z' })]);

    expect(await screen.findByRole('heading', { name: 'Tentativi non riusciti' })).toBeTruthy();
    expect(screen.getByText('· richiesta da te il 16 novembre 2026 · rifiutata da Sam il 17 novembre 2026', { exact: false })).toBeTruthy();
  });

  it('sends the request for the agreement, then opens its stipula', async () =>
  {
    const { user, server, router } = renderApp({
      path: `/coppie/${activeCouple.id}/nuovo-accordo`,
      account: patient,
      answer: (_, path) =>
      {
        if (path === 'GET /api/me/couples')
        {
          return [activeCouple];
        }

        if (path === `POST /api/couples/${activeCouple.id}/agreement-negotiations`)
        {
          return Response.json({ id: ID }, { status: 201 });
        }

        return path === `GET /api/negotiations/${ID}` ? stipula() : undefined;
      },
    });

    expect(await screen.findByText(AGREEMENT_HINT)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Invia la richiesta' }));
    expect(await screen.findByText('Manca il testo dell’accordo.')).toBeTruthy();
    await user.type(screen.getByLabelText('Testo dell’accordo'), TEXT);
    await user.click(screen.getByRole('button', { name: 'Invia la richiesta' }));

    expect(await screen.findByText(REQUEST_SENT)).toBeTruthy();
    expect(router.state.location.pathname).toBe(`/stipule/${ID}`);
    expect(await server.requests.find((sentRequest) => sentRequest.method === 'POST')?.json()).toEqual({ text: TEXT });
  });

  it('asks for a discussion that expands a paragraph', async () =>
  {
    const paragraph = '0190a7e2-0000-7000-8000-0000000000aa';
    const { user, server } = renderApp({
      path: `/conversazioni/${withConversation.conversation}/nuovo-discorso?paragrafo=${paragraph}`,
      account: patient,
      answer: (_, path) =>
      {
        if (path === 'GET /api/me/couples')
        {
          return [withConversation];
        }

        if (path.endsWith('/discussion-negotiations'))
        {
          return Response.json({ id: ID }, { status: 201 });
        }

        return path === `GET /api/negotiations/${ID}` ? stipula() : undefined;
      },
    });

    expect(await screen.findByText(FROM_A_PARAGRAPH)).toBeTruthy();
    await user.type(screen.getByLabelText('Titolo'), 'Le vacanze');
    await user.click(screen.getByRole('button', { name: 'Invia la richiesta' }));
    expect(await screen.findByText('Manca il sottotitolo.')).toBeTruthy();
    await user.type(screen.getByLabelText('Sottotitolo'), 'Dove e con chi');
    await user.click(screen.getByRole('button', { name: 'Invia la richiesta' }));

    await screen.findByText(REQUEST_SENT);
    expect(await server.requests.find((sentRequest) => sentRequest.method === 'POST')?.json()).toEqual({
      title: 'Le vacanze', subtitle: 'Dove e con chi', originParagraphId: paragraph,
    });
  });

  it('is not found by the therapist', async () =>
  {
    renderApp({ path: `/coppie/${activeCouple.id}/nuovo-accordo`, account: therapist });

    expect(await screen.findByRole('heading', { name: 'Pagina non trovata' })).toBeTruthy();
  });
});
