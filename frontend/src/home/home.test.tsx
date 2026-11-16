import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import type { CoupleSummary, Summary } from '../api/client';
import { activeCouple, administrator, endedCouple, invitation, noContent, partner, patient, therapist } from '../api/testing';
import { renderApp } from '../app/testing';
import { NO_COUPLE } from './HomePage';
import { NOTHING_TO_DO, todoItems } from './todo';

const nothing: CoupleSummary = {
  couple: activeCouple.id,
  conversation: null,
  negotiationsWaitingForYou: [],
  unopenedMessages: [],
  closingRequestsToAnswer: [],
  therapistRequestsToDecide: [],
  outcomesToDeclare: [],
  constraintRequestsToDecide: [],
};

describe('the "Da fare"', () =>
{
  it('says in words what waits for the user', () =>
  {
    expect(todoItems({
      ...nothing,
      negotiationsWaitingForYou: [{ id: invitation.id, purpose: 'STARTED_CONVERSATION' }],
      unopenedMessages: [{ discussion: 0, count: 2 }, { discussion: 3, count: 1 }],
      closingRequestsToAnswer: [3],
      therapistRequestsToDecide: [4],
      outcomesToDeclare: [5],
      constraintRequestsToDecide: [invitation.id],
    })).toEqual([
      { text: 'Stipula dell’accordo: tocca a te.', to: `/stipule/${invitation.id}` },
      { text: 'Discorso principale: 2 messaggi da aprire.' },
      { text: 'Discorso 3: 1 messaggio da aprire.' },
      { text: 'Discorso 3: una richiesta di chiusura aspetta la tua risposta.' },
      { text: 'Discorso 4: una richiesta di chiusura aspetta la tua decisione.' },
      { text: 'Discorso 5: dichiara l’esito.' },
      { text: 'Una proposta di vincoli aspetta la tua risposta.' },
    ]);
  });
});

describe('the patient home', () =>
{
  it('shows an invitation, accepted at a click', async () =>
  {
    let summary: Summary = { pendingInvitations: [invitation], couples: [] };
    const { user, server } = renderApp({
      account: patient,
      answer: (_, path) =>
      {
        if (path === 'GET /api/me/summary')
        {
          return summary;
        }

        if (path === 'POST /api/invitations/{id}/accept'.replace('{id}', invitation.id))
        {
          summary = { pendingInvitations: [{ ...invitation, acceptedBy: [patient.id] }], couples: [] };

          return noContent();
        }

        return undefined;
      },
    });

    expect(await screen.findByText('Dott. Rossi ti invita a iniziare un percorso di coppia con Sam. Scade il 20 novembre 2026.')).toBeTruthy();
    expect(screen.queryByText(NO_COUPLE)).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Accetta' }));

    expect(await screen.findByText('Hai accettato: manca la risposta di Sam.')).toBeTruthy();
    expect(server.paths()).toContain(`POST /api/invitations/${invitation.id}/accept`);
  });

  it('declines an invitation only after a confirmation', async () =>
  {
    const { user, server } = renderApp({
      account: patient,
      answer: (_, path) =>
      {
        if (path === 'GET /api/me/summary')
        {
          return { pendingInvitations: [invitation], couples: [] };
        }

        return path.endsWith('/decline') ? noContent() : undefined;
      },
    });

    await user.click(await screen.findByRole('button', { name: 'Rifiuta' }));
    expect(await screen.findByText('Se rifiuti, l’invito si chiude anche per Sam.')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Annulla' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(server.paths()).not.toContain(`POST /api/invitations/${invitation.id}/decline`);

    await user.click(screen.getByRole('button', { name: 'Rifiuta' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Rifiuta' }));
    await waitFor(() => expect(server.paths()).toContain(`POST /api/invitations/${invitation.id}/decline`));
  });

  it('says that an invitation will come when there is no couple yet', async () =>
  {
    renderApp({ account: patient });

    expect(await screen.findByText(NO_COUPLE)).toBeTruthy();
  });

  it('lists what waits, the couple in progress and the ended ones', async () =>
  {
    renderApp({
      account: partner,
      answer: (_, path) =>
      {
        if (path === 'GET /api/me/summary')
        {
          return { pendingInvitations: [], couples: [{ ...nothing, outcomesToDeclare: [2], negotiationsWaitingForYou: [{ id: invitation.id, purpose: 'MODIFIED_AGREEMENT' }] }] };
        }

        return path === 'GET /api/me/couples' ? [activeCouple, endedCouple] : undefined;
      },
    });

    expect(await screen.findByText('Discorso 2: dichiara l’esito.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Modifica dell’accordo: tocca a te.' })).toHaveProperty('pathname', `/stipule/${invitation.id}`);
    const links = screen.getAllByRole('link', { name: /^Alex e / });
    expect(links.map((link) => link.getAttribute('href'))).toEqual([`/coppie/${activeCouple.id}`, `/coppie/${endedCouple.id}`]);
    expect(screen.getByText('Alex e Account eliminato')).toBeTruthy();
    expect(screen.getByText('Terapeuta: Dott. Rossi · Percorso concluso il 13 novembre 2026')).toBeTruthy();
  });

  it('says when nothing waits', async () =>
  {
    renderApp({
      account: patient,
      answer: (_, path) =>
      {
        if (path === 'GET /api/me/summary')
        {
          return { pendingInvitations: [], couples: [nothing] };
        }

        return path === 'GET /api/me/couples' ? [activeCouple] : undefined;
      },
    });

    expect(await screen.findByText(NOTHING_TO_DO)).toBeTruthy();
  });
});

describe('the therapist home', () =>
{
  it('shows the couples, the invitations sent with who accepted, and withdraws one after a confirmation', async () =>
  {
    const { user, server } = renderApp({
      account: therapist,
      answer: (_, path) =>
      {
        if (path === 'GET /api/me/summary')
        {
          return { pendingInvitations: [{ ...invitation, acceptedBy: [partner.id] }], couples: [{ ...nothing, therapistRequestsToDecide: [3] }] };
        }

        if (path === 'GET /api/me/couples')
        {
          return [activeCouple];
        }

        return path.endsWith('/withdraw') ? noContent() : undefined;
      },
    });

    expect(await screen.findByText('Discorso 3: una richiesta di chiusura aspetta la tua decisione.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Invita una coppia' })).toHaveProperty('pathname', '/inviti/nuovo');
    expect(screen.getByText('Ha accettato: Sam · Scade il 20 novembre 2026')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Ritira l’invito' }));
    expect(await screen.findByText('Alex e Sam non potranno più accettarlo.')).toBeTruthy();
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Ritira l’invito' }));

    await waitFor(() => expect(server.paths()).toContain(`POST /api/invitations/${invitation.id}/withdraw`));
  });

  it('has the links of its role in the bar', async () =>
  {
    renderApp({ account: therapist });

    expect(await screen.findByRole('link', { name: 'Coppie' })).toHaveProperty('pathname', '/');
    expect(screen.getByRole('link', { name: 'Invita' })).toHaveProperty('pathname', '/inviti/nuovo');
    expect(screen.queryByRole('link', { name: 'Terapeuti' })).toBeNull();
  });
});

describe('the administrator home', () =>
{
  it('is the administration', async () =>
  {
    const { router } = renderApp({ account: administrator, answer: (_, path) => (path === 'GET /api/admin/therapists' ? [] : undefined) });

    expect(await screen.findByRole('heading', { name: 'Terapeuti' })).toBeTruthy();
    expect(router.state.location.pathname).toBe('/amministrazione');
  });
});
