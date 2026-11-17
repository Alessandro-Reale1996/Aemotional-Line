import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import type { Account, ConstraintRequest, Constraints, Couple } from '../api/client';
import { constraint, CONVERSATION, conversationWith, noContent, partner, patient, problem, talkingCouple, therapist } from '../api/testing';
import { renderApp } from '../app/testing';
import { ABANDON_TEXT } from '../conversation/Composer';
import { discardUnsentText, hasUnsentText } from '../session/unsent';
import { LIFTS_ALL, NONE_IN_FORCE, REJECT_TEXT, WAIT_FOR_PENDING, WITHDRAW_TEXT } from './ConstraintsPage';

const CONSTRAINTS = `/api/conversations/${CONVERSATION}/constraints`;
const HOME = { latitude: 45.4642, longitude: 9.19 };
const REQUEST = '0190a7e2-0000-7000-8000-0000000000f1';

function pending(extra: Partial<ConstraintRequest> = {}): ConstraintRequest
{
  return {
    id: REQUEST,
    proposedBy: therapist.id,
    assignments: [constraint(patient.id, { type: 'TIME', notBefore: '18:00:00' })],
    status: 'PENDING',
    approvals: [],
    proposedAt: '2026-11-17T09:00:00Z',
    closedAt: null,
    rejectedBy: null,
    previousAssignments: null,
    ...extra,
  };
}

/** The constraints page as `account` sees it; the constraints are re-read from `state` after every command. */
function renderConstraints(constraints: Constraints, account: Account = patient, couple: Couple = talkingCouple, answer: (path: string) => unknown = () => undefined)
{
  const state = { constraints };
  const rendered = renderApp({
    path: `/conversazioni/${CONVERSATION}/vincoli`,
    account,
    answer: (_, path) =>
    {
      const given = answer(path);

      if (given !== undefined)
      {
        return given;
      }

      if (path === `GET ${CONSTRAINTS}`)
      {
        return state.constraints;
      }

      if (path === `POST ${CONSTRAINTS}/requests`)
      {
        return Response.json({ id: REQUEST }, { status: 201 });
      }

      if (path.startsWith(`POST ${CONSTRAINTS}/requests/`))
      {
        return noContent();
      }

      return path === 'GET /api/me/couples' ? [couple] : undefined;
    },
  });

  return { ...rendered, state };
}

function region(name: string | RegExp): HTMLElement
{
  return screen.getByRole('region', { name });
}

afterEach(() => discardUnsentText());

describe('the constraints in force', () =>
{
  it('are said in words; the place shows on a map to whom it binds, its numbers to the therapist', async () =>
  {
    const inForce = [
      constraint(partner.id, { type: 'LOCATION', scope: 'READ', ...HOME, radiusMetres: 500, direction: 'WITHIN' }),
      constraint(patient.id, { type: 'REPLY_DELAY', delay: 'PT2H' }),
    ];
    renderConstraints({ inForce, requests: [] }, partner);

    expect(await screen.findByText('Alex deve attendere 2 ore dall’apertura di un messaggio prima di rispondere.')).toBeTruthy();
    const place = screen.getByText('Puoi aprire i messaggi solo entro 500 metri dal luogo concordato.').closest('li') as HTMLElement;
    const link = within(place).getByRole('link', { name: 'Apri su OpenStreetMap' });
    expect(link.getAttribute('href')).toBe('https://www.openstreetmap.org/?mlat=45.4642&mlon=9.19#map=16/45.4642/9.19');
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toBe('noopener noreferrer');
    expect(within(place).queryByText('45,46420, 9,19000'), 'the numbers are for the therapist').toBeNull();
    expect(screen.queryByRole('button', { name: 'Proponi nuovi vincoli' })).toBeNull();

    document.body.innerHTML = '';
    // The API leaves out the other partner's place (F9 decision 1): no map to open.
    renderConstraints({ inForce: [{ ...inForce[0], latitude: null, longitude: null } as never], requests: [] }, patient);
    expect(await screen.findByText('Sam può aprire i messaggi solo entro 500 metri dal luogo concordato.')).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Apri su OpenStreetMap' })).toBeNull();

    document.body.innerHTML = '';
    renderConstraints({ inForce, requests: [] }, therapist);
    expect(await screen.findByText('45,46420, 9,19000')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Apri su OpenStreetMap' })).toBeTruthy();
  });

  it('may be none', async () =>
  {
    renderConstraints({ inForce: [], requests: [] });

    expect(await screen.findByText(NONE_IN_FORCE)).toBeTruthy();
  });
});

describe('a request', () =>
{
  it('is approved after a confirmation saying when it takes effect, and can be rejected until it does', async () =>
  {
    const { user, server, state } = renderConstraints({ inForce: [], requests: [pending()] });
    const card = await screen.findByRole('region', { name: 'Proposta di Dott. Rossi del 17 novembre 2026' });
    expect(within(card).getByText('Puoi scrivere solo dalle 18:00 a mezzanotte.')).toBeTruthy();
    expect(within(card).getByText('Nessuno l’ha ancora approvata.')).toBeTruthy();
    expect(within(card).getByText('In attesa di approvazione')).toBeTruthy();

    await user.click(within(card).getByRole('button', { name: 'Approva' }));
    const dialog = await screen.findByRole('dialog', { name: 'Approvare i vincoli?' });
    expect(within(dialog).getByText('Approvando, accetti questi vincoli: entreranno in vigore quando li avrà approvati anche Sam.')).toBeTruthy();
    state.constraints = { inForce: [], requests: [pending({ approvals: [patient.id] })] };
    await user.click(within(dialog).getByRole('button', { name: 'Approva' }));
    await waitFor(() => expect(server.paths()).toContain(`POST ${CONSTRAINTS}/requests/${REQUEST}/approve`));

    expect(await screen.findByText('Hai approvato: manca l’approvazione di Sam.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Approva' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Rifiuta' }));
    const rejecting = await screen.findByRole('dialog', { name: 'Rifiutare la proposta?' });
    expect(within(rejecting).getByText(REJECT_TEXT)).toBeTruthy();
    await user.click(within(rejecting).getByRole('button', { name: 'Rifiuta la proposta' }));
    await waitFor(() => expect(server.paths()).toContain(`POST ${CONSTRAINTS}/requests/${REQUEST}/reject`));
  });

  it('takes effect at once with the second approval, and says so', async () =>
  {
    const { user } = renderConstraints({ inForce: [], requests: [pending({ approvals: [partner.id] })] });
    await user.click(await screen.findByRole('button', { name: 'Approva' }));

    expect(await screen.findByText('Approvando, questi vincoli entrano subito in vigore al posto di quelli attuali.')).toBeTruthy();
  });

  it('is withdrawn by the therapist after a confirmation, who can’t propose another meanwhile', async () =>
  {
    const { user, server } = renderConstraints({ inForce: [], requests: [pending({ approvals: [partner.id] })] }, therapist);
    const card = await screen.findByRole('region', { name: 'La tua proposta del 17 novembre 2026' });
    expect(within(card).getByText('Approvata da Sam: manca l’approvazione di Alex.')).toBeTruthy();
    expect(within(card).queryByRole('button', { name: 'Approva' })).toBeNull();
    expect(screen.getByText(WAIT_FOR_PENDING)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Proponi nuovi vincoli' })).toBeNull();

    await user.click(within(card).getByRole('button', { name: 'Ritira la proposta' }));
    const dialog = await screen.findByRole('dialog', { name: 'Ritirare la proposta?' });
    expect(within(dialog).getByText(WITHDRAW_TEXT)).toBeTruthy();
    await user.click(within(dialog).getByRole('button', { name: 'Ritira la proposta' }));
    await waitFor(() => expect(server.paths()).toContain(`POST ${CONSTRAINTS}/requests/${REQUEST}/withdraw`));
  });

  it('that is over says how, and what it replaced', async () =>
  {
    const evening = constraint(patient.id, { type: 'TIME', notBefore: '18:00:00' });
    renderConstraints({
      inForce: [],
      requests: [
        pending({ status: 'APPLIED', approvals: [patient.id, partner.id], closedAt: '2026-11-17T10:00:00Z', previousAssignments: [] }),
        pending({ id: 'b', status: 'REJECTED', rejectedBy: partner.id, closedAt: '2026-11-17T11:00:00Z', proposedAt: '2026-11-17T10:30:00Z' }),
        pending({ id: 'c', status: 'WITHDRAWN', closedAt: '2026-11-17T12:00:00Z', proposedAt: '2026-11-17T11:30:00Z' }),
        pending({ id: 'd', assignments: [], status: 'APPLIED', approvals: [patient.id, partner.id], closedAt: '2026-11-17T13:00:00Z', proposedAt: '2026-11-17T12:30:00Z', previousAssignments: [evening] }),
      ],
    }, partner);

    const previous = await screen.findByRole('heading', { name: 'Proposte precedenti' });
    const section = previous.parentElement as HTMLElement;
    expect(within(section).getByText('Approvata da entrambi il 17 novembre 2026, 11:00.')).toBeTruthy();
    expect(within(section).getByText('Rifiutata da te il 17 novembre 2026, 12:00.')).toBeTruthy();
    expect(within(section).getByText('Ritirata il 17 novembre 2026, 13:00.')).toBeTruthy();
    expect(within(section).getByText(LIFTS_ALL)).toBeTruthy();
    const [first, last] = within(section).getAllByText('Sostituiva:').map((label) => label.nextElementSibling as HTMLElement);
    expect(within(first as HTMLElement).getByText('Nessun vincolo.'), 'the first one replaced nothing').toBeTruthy();
    const replaced = last as HTMLElement;
    expect(within(replaced).getByText('Alex può scrivere solo dalle 18:00 a mezzanotte.'), 'what the last one replaced').toBeTruthy();
    expect(within(section).queryAllByRole('button')).toEqual([]);
  });

  it('can’t be answered once the couple has ended', async () =>
  {
    renderConstraints({ inForce: [], requests: [pending()] }, patient, { ...talkingCouple, active: false, endedAt: '2026-11-17T12:00:00Z' });

    const card = await screen.findByRole('region', { name: 'Proposta di Dott. Rossi del 17 novembre 2026' });
    expect(within(card).queryAllByRole('button')).toEqual([]);
  });
});

describe('a proposal', () =>
{
  it('starts from the constraints in force, is checked, reviewed and sent whole', async () =>
  {
    const inForce = [constraint(partner.id, { type: 'REPLY_DELAY', delay: 'PT2H' })];
    const { user, server } = renderConstraints({ inForce, requests: [] }, therapist);
    await user.click(await screen.findByRole('button', { name: 'Proponi nuovi vincoli' }));

    const first = region('Vincolo 1');
    expect(within(first).getByLabelText('Per')).toHaveProperty('value', partner.id);
    expect(within(first).getByLabelText('Durata')).toHaveProperty('value', '2');
    expect(within(first).getByLabelText('Unità')).toHaveProperty('value', 'HOURS');

    await user.click(screen.getByRole('button', { name: 'Aggiungi un vincolo' }));
    const second = region('Vincolo 2');
    await user.selectOptions(within(second).getByLabelText('Tipo'), 'LOCATION');
    await user.click(screen.getByRole('button', { name: 'Rivedi e proponi' }));
    expect(await screen.findByText('Vincolo 2: manca la latitudine.')).toBeTruthy();
    expect(screen.queryByRole('dialog')).toBeNull();

    await user.type(within(second).getByLabelText('Latitudine'), '45,4642');
    await user.type(within(second).getByLabelText('Longitudine'), '9,19');
    expect(within(second).getByRole('link', { name: 'Apri su OpenStreetMap' }).getAttribute('href')).toContain('mlat=45.4642&mlon=9.19');
    await user.type(within(second).getByLabelText('Raggio in metri'), '300');
    await user.selectOptions(within(second).getByLabelText('Direzione'), 'BEYOND');
    await user.selectOptions(within(second).getByLabelText('Vale per'), 'BOTH');
    await user.click(screen.getByRole('button', { name: 'Aggiungi un vincolo' }));
    const third = region('Vincolo 3');
    await user.selectOptions(within(third).getByLabelText('Per'), partner.id);
    await user.type(within(third).getByLabelText('Dalle'), '18:30');
    await user.click(screen.getByRole('button', { name: 'Rivedi e proponi' }));

    const review = await screen.findByRole('dialog', { name: 'Proporre questi vincoli?' });
    expect(within(review).getAllByRole('listitem').map((item) => item.firstChild?.textContent)).toEqual([
      'Alex può aprire i messaggi e scrivere solo a più di 300 metri dal luogo concordato.',
      'Sam deve attendere 2 ore dall’apertura di un messaggio prima di rispondere.',
      'Sam può scrivere solo dalle 18:30 a mezzanotte.',
    ]);
    expect(within(review).getByText('Entreranno in vigore quando Alex e Sam li avranno approvati entrambi.')).toBeTruthy();
    await user.click(within(review).getByRole('button', { name: 'Proponi' }));

    await waitFor(() => expect(server.paths()).toContain(`POST ${CONSTRAINTS}/requests`));
    expect(await server.requests.find((sent) => sent.method === 'POST')?.json()).toEqual({
      constraints: [
        { assignee: partner.id, type: 'REPLY_DELAY', delay: 'PT2H' },
        { assignee: patient.id, type: 'LOCATION', scope: 'BOTH', ...HOME, radiusMetres: 300, direction: 'BEYOND' },
        { assignee: partner.id, type: 'TIME', scope: 'WRITE', notBefore: '18:30' },
      ],
    });
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Nuova proposta' })).toBeNull());
    expect(hasUnsentText(), 'proposed: nothing to protect any more').toBe(false);
  });

  it('may lift every constraint', async () =>
  {
    const { user, server } = renderConstraints({ inForce: [constraint(patient.id, { notBefore: '09:00:00' })], requests: [] }, therapist);
    await user.click(await screen.findByRole('button', { name: 'Proponi nuovi vincoli' }));
    await user.click(within(region('Vincolo 1')).getByRole('button', { name: 'Rimuovi' }));
    expect(within(region('Nuova proposta')).getByText(LIFTS_ALL)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Rivedi e proponi' }));
    const review = await screen.findByRole('dialog', { name: 'Proporre questi vincoli?' });
    expect(within(review).getByText(LIFTS_ALL)).toBeTruthy();
    await user.click(within(review).getByRole('button', { name: 'Proponi' }));

    await waitFor(() => expect(server.paths()).toContain(`POST ${CONSTRAINTS}/requests`));
    expect(await server.requests.find((sent) => sent.method === 'POST')?.json()).toEqual({ constraints: [] });
  });

  it('asks before being given up once changed, and shows what the server refused', async () =>
  {
    const { user } = renderConstraints({ inForce: [], requests: [] }, therapist, talkingCouple,
      (path) => (path === `POST ${CONSTRAINTS}/requests` ? problem(409, 'CONSTRAINT_REQUEST_PENDING') : undefined));
    await user.click(await screen.findByRole('button', { name: 'Proponi nuovi vincoli' }));
    await user.click(screen.getByRole('button', { name: 'Rinuncia alla proposta' }));
    expect(screen.queryByRole('region', { name: 'Nuova proposta' }), 'nothing changed: no question').toBeNull();

    await user.click(screen.getByRole('button', { name: 'Proponi nuovi vincoli' }));
    expect(hasUnsentText(), 'opened, not changed').toBe(false);
    await user.click(screen.getByRole('button', { name: 'Aggiungi un vincolo' }));
    expect(hasUnsentText(), 'changed: protected like any unsent text').toBe(true);
    await user.type(within(region('Vincolo 1')).getByLabelText('Dalle'), '09:00');
    await user.click(screen.getByRole('button', { name: 'Rinuncia alla proposta' }));
    expect(await screen.findByText(ABANDON_TEXT)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Continua a modificare' }));

    await user.click(screen.getByRole('button', { name: 'Rivedi e proponi' }));
    await user.click(within(await screen.findByRole('dialog', { name: 'Proporre questi vincoli?' })).getByRole('button', { name: 'Proponi' }));
    expect(await screen.findByText('C’è già una proposta di vincoli in attesa di risposta.')).toBeTruthy();
    expect(within(region('Vincolo 1')).getByLabelText('Dalle')).toHaveProperty('value', '09:00');
  });

  it('is the therapist’s only, while the couple is active', async () =>
  {
    renderConstraints({ inForce: [], requests: [] }, therapist, { ...talkingCouple, active: false, endedAt: '2026-11-17T12:00:00Z' });

    expect(await screen.findByText(NONE_IN_FORCE)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Proponi nuovi vincoli' })).toBeNull();
  });
});

describe('the ways to the constraints', () =>
{
  it('are the conversation page and the “Da fare”', async () =>
  {
    renderApp({
      path: `/conversazioni/${CONVERSATION}`,
      account: patient,
      answer: (_, path) => (path === `GET /api/conversations/${CONVERSATION}` ? conversationWith([]) : path === 'GET /api/me/couples' ? [talkingCouple] : undefined),
    });
    expect(await screen.findByRole('link', { name: 'Vincoli' })).toHaveProperty('pathname', `/conversazioni/${CONVERSATION}/vincoli`);

    document.body.innerHTML = '';
    renderApp({
      account: patient,
      answer: (_, path) => (path === 'GET /api/me/summary'
        ? {
          pendingInvitations: [],
          couples: [{
            couple: talkingCouple.id, conversation: CONVERSATION, negotiationsWaitingForYou: [], unopenedMessages: [], closingRequestsToAnswer: [],
            outcomesToDeclare: [], therapistRequestsToDecide: [], constraintRequestsToDecide: [REQUEST],
          }],
        }
        : path === 'GET /api/me/couples' ? [talkingCouple] : undefined),
    });
    expect(await screen.findByRole('link', { name: 'Una proposta di vincoli aspetta la tua risposta.' }))
      .toHaveProperty('pathname', `/conversazioni/${CONVERSATION}/vincoli`);
  });
});
