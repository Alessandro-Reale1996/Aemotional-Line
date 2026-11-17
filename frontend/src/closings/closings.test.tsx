import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import type { Account, Closing, ClosingRequest, Conversation, Couple, TherapistRequest } from '../api/client';
import { CONVERSATION, conversationWith, noContent, openClosing, partner, patient, problem, talkingCouple, therapist } from '../api/testing';
import { renderApp } from '../app/testing';
import { ABANDON_TEXT } from '../conversation/Composer';
import { discardUnsentText, hasUnsentText } from '../session/unsent';
import { CLOSE_TEXT, NEEDS_BOTH } from './ClosingPanel';

const CLOSING = `/api/conversations/${CONVERSATION}/discussions/1/closing`;

function withADiscussion(closed = false): Conversation
{
  const conversation = conversationWith([]);
  conversation.discussions.push({ id: 1, title: 'Le vacanze', subtitle: 'Dove e con chi', closed, originParagraphId: null, lastSender: null, messages: [] });

  return conversation;
}

function request(requester: string, status: ClosingRequest['status'], minute = 0): ClosingRequest
{
  return { requester, sentAt: `2026-11-17T09:0${minute}:00Z`, status, endedAt: status === 'PENDING' ? null : `2026-11-17T09:0${minute + 1}:00Z` };
}

function toTheTherapist(requester: string, justification: string | null, previous: ClosingRequest[]): TherapistRequest
{
  return { requester, justification, sentAt: '2026-11-17T09:30:00Z', previousRequests: previous, status: 'PENDING', decidedAt: null };
}

/** The page of discussion 1 as `account` sees it; the closing is re-read from `state` after every command. */
function renderClosing(closing: Closing, account: Account = patient, couple: Couple = talkingCouple, answer: (path: string) => unknown = () => undefined)
{
  const state = { closing, conversation: withADiscussion(closing.closed) };
  const rendered = renderApp({
    path: `/conversazioni/${CONVERSATION}/discorsi/1`,
    account,
    answer: (_, path) =>
    {
      const given = answer(path);

      if (given !== undefined)
      {
        return given;
      }

      if (path === `GET ${CLOSING}`)
      {
        return state.closing;
      }

      if (path.startsWith(`POST ${CLOSING}`))
      {
        return noContent();
      }

      if (path === `GET /api/conversations/${CONVERSATION}`)
      {
        return state.conversation;
      }

      if (path.endsWith('/constraints'))
      {
        return { inForce: [], requests: [] };
      }

      return path === 'GET /api/me/couples' ? [couple] : undefined;
    },
  });

  return { ...rendered, state };
}

function panel(): HTMLElement
{
  return screen.getByRole('region', { name: 'Chiusura del discorso' });
}

afterEach(() => discardUnsentText());

describe('the closing of a discussion', () =>
{
  it('isn’t offered for the main discussion, which is never read', async () =>
  {
    const { server } = renderApp({
      path: `/conversazioni/${CONVERSATION}/discorsi/0`,
      account: patient,
      answer: (_, path) => (path === `GET /api/conversations/${CONVERSATION}` ? withADiscussion() : path.endsWith('/constraints') ? { inForce: [], requests: [] }
        : path === 'GET /api/me/couples' ? [talkingCouple] : undefined),
    });

    await screen.findByRole('status');
    expect(screen.queryByRole('region', { name: 'Chiusura del discorso' })).toBeNull();
    expect(server.paths().some((path) => path.endsWith('/closing'))).toBe(false);
  });

  it('is asked of the other partner, who accepts after a confirmation or refuses', async () =>
  {
    const asking = renderClosing(openClosing(1));
    expect(await screen.findByText(NEEDS_BOTH)).toBeTruthy();
    expect(within(panel()).queryByRole('button', { name: /^Chiedi a/ }), 'no failed attempt yet').toBeNull();
    await asking.user.click(screen.getByRole('button', { name: 'Chiedi di chiudere il discorso' }));
    await waitFor(() => expect(asking.server.paths()).toContain(`POST ${CLOSING}/request`));
    asking.router.dispose();
    document.body.innerHTML = '';

    const answering = renderClosing(openClosing(1, { requests: [request(patient.id, 'PENDING')] }), partner);
    expect(await screen.findByText('Alex chiede di chiudere il discorso (17 novembre 2026, 10:00).')).toBeTruthy();
    await answering.user.click(screen.getByRole('button', { name: 'Accetta e chiudi' }));
    const dialog = await screen.findByRole('dialog', { name: 'Chiudere il discorso?' });
    expect(within(dialog).getByText(CLOSE_TEXT)).toBeTruthy();
    expect(answering.server.paths()).not.toContain(`POST ${CLOSING}/accept`);
    await answering.user.click(within(dialog).getByRole('button', { name: 'Chiudi il discorso' }));
    await waitFor(() => expect(answering.server.paths()).toContain(`POST ${CLOSING}/accept`));

    await answering.user.click(screen.getByRole('button', { name: 'Rifiuta' }));
    await waitFor(() => expect(answering.server.paths()).toContain(`POST ${CLOSING}/refuse`));
  });

  it('can be withdrawn by whoever asked; the therapist only reads it', async () =>
  {
    const pending = openClosing(1, { requests: [request(patient.id, 'PENDING')] });
    const { user, server } = renderClosing(pending);
    expect(await screen.findByText('Hai chiesto a Sam di chiudere il discorso il 17 novembre 2026, 10:00. In attesa della sua risposta.')).toBeTruthy();
    expect(within(panel()).queryByRole('button', { name: 'Accetta e chiudi' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Ritira la richiesta' }));
    await waitFor(() => expect(server.paths()).toContain(`POST ${CLOSING}/withdraw`));

    document.body.innerHTML = '';
    renderClosing(pending, therapist);
    expect(await screen.findByText('Alex ha chiesto a Sam di chiudere il discorso il 17 novembre 2026, 10:00.')).toBeTruthy();
    expect(within(panel()).queryAllByRole('button')).toEqual([]);
  });

  it('goes to the therapist after a failed attempt, with a justification the other partner never reads', async () =>
  {
    const refused = request(patient.id, 'REFUSED');
    const { user, server } = renderClosing(openClosing(1, { requests: [refused] }));
    expect(await screen.findByText('Hai già provato a chiuderlo con Sam senza riuscirci: puoi chiedere a Dott. Rossi di chiuderlo.')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Chiedi a Dott. Rossi' }));
    expect(screen.getByText(/Sam saprà della richiesta, ma non leggerà la motivazione\./)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Invia la richiesta' }));
    expect(await screen.findByText('Manca la motivazione.')).toBeTruthy();

    // Giving up asks first, since the text would be lost.
    // Sent as typed, line breaks and spaces included: the server never alters the user's text, nor does the app.
    await user.type(screen.getByLabelText('Motivazione'), 'Ne abbiamo parlato abbastanza.{Enter}Basta così. ');
    expect(hasUnsentText()).toBe(true);
    await user.click(screen.getByRole('button', { name: 'Annulla' }));
    expect(await screen.findByText(ABANDON_TEXT)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Continua a scrivere' }));

    await user.click(screen.getByRole('button', { name: 'Invia la richiesta' }));
    await waitFor(() => expect(server.paths()).toContain(`POST ${CLOSING}/therapist-request`));
    expect(await server.requests.find((sent) => sent.url.endsWith('/therapist-request'))?.json()).toEqual({ justification: 'Ne abbiamo parlato abbastanza.\nBasta così. ' });
    await waitFor(() => expect(hasUnsentText(), 'sent: nothing to protect any more').toBe(false));
  });

  it('asks to try again with the other after the therapist said no', async () =>
  {
    const refused = request(patient.id, 'REFUSED');
    renderClosing(openClosing(1, { requests: [refused], therapistRequests: [{ ...toTheTherapist(patient.id, 'Basta così.', [refused]), status: 'REFUSED', decidedAt: '2026-11-17T09:31:00Z' }] }));

    expect(await screen.findByText('Per chiedere di nuovo a Dott. Rossi, prova prima ancora a chiuderlo con Sam.')).toBeTruthy();
    expect(within(panel()).queryByRole('button', { name: 'Chiedi a Dott. Rossi' })).toBeNull();
    expect(within(panel()).getByText(/^La tua richiesta a Dott\. Rossi · inviata il 17 novembre 2026, 10:30 · Non accolta il/)).toBeTruthy();
  });

  it('shows the justification to the requester and the therapist only', async () =>
  {
    const refused = request(patient.id, 'REFUSED');
    renderClosing(openClosing(1, { requests: [refused], therapistRequests: [toTheTherapist(patient.id, null, [refused])] }), partner);
    expect(await screen.findByText('Alex ha chiesto a Dott. Rossi di chiudere il discorso il 17 novembre 2026, 10:30. In attesa della sua decisione.')).toBeTruthy();
    expect(within(panel()).queryByText('Motivazione')).toBeNull();
    expect(within(panel()).queryAllByRole('button')).toEqual([]);

    document.body.innerHTML = '';
    renderClosing(openClosing(1, { requests: [refused], therapistRequests: [toTheTherapist(patient.id, 'Basta così.', [refused])] }));
    expect(await screen.findByText('Hai chiesto a Dott. Rossi di chiudere il discorso il 17 novembre 2026, 10:30. In attesa della sua decisione.')).toBeTruthy();
    expect(screen.getByText('Basta così.')).toBeTruthy();
  });

  it('is decided by the therapist, who sees the earlier requests with their times', async () =>
  {
    const earlier = [request(patient.id, 'WITHDRAWN', 0), request(patient.id, 'REFUSED', 2)];
    const { user, server } = renderClosing(openClosing(1, { requests: earlier, therapistRequests: [toTheTherapist(patient.id, 'Basta così.', earlier)] }), therapist);

    expect(await screen.findByText('Alex ti chiede di chiudere il discorso (17 novembre 2026, 10:30).')).toBeTruthy();
    expect(within(panel()).getByText('Basta così.')).toBeTruthy();
    const previous = within(panel()).getByRole('heading', { name: 'Richieste di chiusura precedenti' }).nextElementSibling as HTMLElement;
    expect(within(previous).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Richiesta di Alex · inviata il 17 novembre 2026, 10:00 · Ritirata il 17 novembre 2026, 10:01',
      'Richiesta di Alex · inviata il 17 novembre 2026, 10:02 · Rifiutata il 17 novembre 2026, 10:03',
    ]);

    await user.click(screen.getByRole('button', { name: 'Non chiudere' }));
    await waitFor(() => expect(server.paths()).toContain(`POST ${CLOSING}/therapist-request/refuse`));
    await user.click(screen.getByRole('button', { name: 'Chiudi il discorso' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Chiudi il discorso' }));
    await waitFor(() => expect(server.paths()).toContain(`POST ${CLOSING}/therapist-request/confirm`));
  });

  it('says nothing to the therapist while nobody has asked anything', async () =>
  {
    renderClosing(openClosing(1), therapist);

    expect(await screen.findByText('Si aspetta il primo messaggio.')).toBeTruthy();
    expect(screen.queryByRole('region', { name: 'Chiusura del discorso' })).toBeNull();
  });

  it('is frozen once the couple has ended', async () =>
  {
    renderClosing(openClosing(1, { requests: [request(partner.id, 'PENDING')] }), patient, { ...talkingCouple, active: false, endedAt: '2026-11-17T12:00:00Z' });

    expect(await screen.findByText('Sam chiede di chiudere il discorso (17 novembre 2026, 10:00).')).toBeTruthy();
    expect(within(panel()).queryAllByRole('button')).toEqual([]);
  });
});

describe('the outcome', () =>
{
  const closed = (extra: Partial<Closing> = {}) => openClosing(1, { closed: true, way: 'AGREEMENT', closedAt: '2026-11-17T09:10:00Z', ...extra });

  it('is declared after a confirmation, the other’s staying hidden until then', async () =>
  {
    const { user, server, state } = renderClosing(closed({ declarations: [{ partner: partner.id, outcome: null, reason: null, declaredAt: '2026-11-17T09:20:00Z' }] }));

    expect(await screen.findByText('Il discorso è stato chiuso il 17 novembre 2026, 10:10 di comune accordo.')).toBeTruthy();
    expect(screen.getByText('Sam ha dichiarato il suo esito: lo vedrai quando avrai dichiarato anche tu.')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Dichiara l’esito' }));
    expect(await screen.findByText('Scegli un esito.')).toBeTruthy();
    await user.click(screen.getByLabelText('Arenato'));
    await user.click(screen.getByRole('button', { name: 'Dichiara l’esito' }));
    expect(await screen.findByText('Manca il motivo.')).toBeTruthy();
    await user.type(screen.getByLabelText('Motivo'), 'Non ci siamo capiti.');
    await user.click(screen.getByRole('button', { name: 'Dichiara l’esito' }));

    const dialog = await screen.findByRole('dialog', { name: 'Dichiarare l’esito?' });
    expect(within(dialog).getByText('Esito: Arenato. Una volta dichiarato non si può più modificare.')).toBeTruthy();
    state.closing = closed({
      declarations: [
        { partner: partner.id, outcome: 'RESOLVED', reason: 'Per me è chiaro.', declaredAt: '2026-11-17T09:20:00Z' },
        { partner: patient.id, outcome: 'STALLED', reason: 'Non ci siamo capiti.', declaredAt: '2026-11-17T09:25:00Z' },
      ],
    });
    await user.click(within(dialog).getByRole('button', { name: 'Dichiara' }));

    await waitFor(() => expect(server.paths()).toContain(`POST ${CLOSING}/outcome`));
    expect(await server.requests.find((sent) => sent.url.endsWith('/outcome'))?.json()).toEqual({ outcome: 'STALLED', reason: 'Non ci siamo capiti.' });
    expect(await screen.findByText('Il tuo esito: Arenato · 17 novembre 2026, 10:25')).toBeTruthy();
    expect(screen.getByText('Esito di Sam: Risolto · 17 novembre 2026, 10:20')).toBeTruthy();
    expect(screen.getByText('Per me è chiaro.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Dichiara l’esito' })).toBeNull();
  });

  it('keeps what was written when the server refuses it, even if a re-read would remove the form', async () =>
  {
    const declared = closed({ declarations: [{ partner: patient.id, outcome: 'STALLED', reason: 'Da un altro dispositivo.', declaredAt: '2026-11-17T09:20:00Z' }] });
    const { user, state } = renderClosing(closed(), patient, talkingCouple, (path) =>
    {
      if (path !== `POST ${CLOSING}/outcome`)
      {
        return undefined;
      }

      // Declared meanwhile from another device: the form would disappear with a re-read.
      state.closing = declared;

      return problem(409, 'OUTCOME_ALREADY_DECLARED');
    });
    await user.click(await screen.findByLabelText('Risolto'));
    expect(hasUnsentText(), 'a chosen outcome is unsent').toBe(true);
    await user.type(screen.getByLabelText('Motivo'), 'Ci siamo chiariti.');
    await user.click(screen.getByRole('button', { name: 'Dichiara l’esito' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Dichiara' }));

    expect(await screen.findByText('Hai già dichiarato l’esito di questo discorso.')).toBeTruthy();
    expect(screen.getByLabelText('Motivo')).toHaveProperty('value', 'Ci siamo chiariti.');
  });

  it('is shown to the therapist as soon as it is declared, and closed by the therapist reads so', async () =>
  {
    const refused = request(partner.id, 'REFUSED');
    renderClosing(closed({
      way: 'THERAPIST',
      requests: [refused],
      therapistRequests: [{ ...toTheTherapist(partner.id, 'Basta così.', [refused]), status: 'CONFIRMED', decidedAt: '2026-11-17T09:10:00Z' }],
      declarations: [{ partner: partner.id, outcome: 'RESOLVED', reason: 'Per me è chiaro.', declaredAt: '2026-11-17T09:20:00Z' }],
    }), therapist);

    expect(await screen.findByText('Il discorso è stato chiuso il 17 novembre 2026, 10:10 da te, su richiesta di Sam.')).toBeTruthy();
    expect(screen.getByText('Esito di Sam: Risolto · 17 novembre 2026, 10:20')).toBeTruthy();
    expect(screen.getByText('Alex non ha ancora dichiarato l’esito.')).toBeTruthy();
    expect(within(panel()).queryByRole('button')).toBeNull();
  });
});
