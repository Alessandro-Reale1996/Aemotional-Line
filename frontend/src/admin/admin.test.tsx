import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { administrator, endedCouple, noContent, patient, therapists } from '../api/testing';
import { renderApp } from '../app/testing';
import { DELETE_DATA_TEXT } from '../couples/CouplePage';
import { disableText, LINK_SENT } from './AdministrationPage';

const [pending, active, disabled] = therapists as [typeof therapists[0], typeof therapists[0], typeof therapists[0]];

function answers(extra: (request: Request, path: string) => unknown = () => undefined)
{
  return (request: Request, path: string) => extra(request, path) ?? (path === 'GET /api/admin/therapists' ? therapists : undefined);
}

describe('the administration', () =>
{
  it('lists the therapists with their status', async () =>
  {
    renderApp({ path: '/amministrazione', account: administrator, answer: answers() });

    expect(await screen.findByText('verdi@example.test · In attesa di attivazione')).toBeTruthy();
    expect(screen.getByText('neri@example.test · Attivo')).toBeTruthy();
    expect(screen.getByText('bianchi@example.test · Disattivato')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Percorsi conclusi' })).toHaveProperty('pathname', `/amministrazione/terapeuti/${disabled.id}`);
    expect(screen.getByRole('link', { name: 'Terapeuti' })).toHaveProperty('pathname', '/amministrazione');
  });

  it('creates a therapist', async () =>
  {
    const { user, server } = renderApp({
      path: '/amministrazione',
      account: administrator,
      answer: answers((_, path) => (path === 'POST /api/admin/therapists' ? Response.json({ id: pending.id }, { status: 201 }) : undefined)),
    });

    await user.type(await screen.findByLabelText('Nome'), 'Dott.ssa Gialli');
    await user.type(screen.getByLabelText('Email'), 'gialli@example.test');
    await user.click(screen.getByRole('button', { name: 'Crea l’account' }));

    await waitFor(() => expect(screen.getByLabelText('Nome')).toHaveProperty('value', ''));
    expect(await server.requests.find((request) => request.method === 'POST')?.json()).toEqual({ displayName: 'Dott.ssa Gialli', email: 'gialli@example.test' });
  });

  it('sends a new link to a therapist still pending', async () =>
  {
    const { user, server } = renderApp({
      path: '/amministrazione',
      account: administrator,
      answer: answers((_, path) => (path.endsWith('/activation-link') ? noContent() : undefined)),
    });

    await user.click(await screen.findByRole('button', { name: 'Invia di nuovo il link' }));

    expect(await screen.findByText(LINK_SENT)).toBeTruthy();
    expect(server.paths()).toContain(`POST /api/admin/therapists/${pending.id}/activation-link`);
  });

  it('disables a therapist with the password, after saying what happens', async () =>
  {
    const { user, server } = renderApp({
      path: '/amministrazione',
      account: administrator,
      answer: answers((_, path) => (path.endsWith('/disable') ? noContent() : undefined)),
    });

    await user.click(await screen.findByRole('button', { name: 'Disattiva' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(disableText('Dott. Neri'))).toBeTruthy();
    await user.type(within(dialog).getByLabelText('La tua password'), 'la mia password lunga');
    await user.click(within(dialog).getByRole('button', { name: 'Disattiva' }));

    await waitFor(() => expect(server.paths()).toContain(`POST /api/admin/therapists/${active.id}/disable`));
    expect(await server.requests.find((request) => request.url.endsWith('/disable'))?.json()).toEqual({ password: 'la mia password lunga' });
  });

  it('shows a disabled therapist ended couples, by date only, and deletes their data', async () =>
  {
    const { user, server } = renderApp({
      path: `/amministrazione/terapeuti/${disabled.id}`,
      account: administrator,
      answer: answers((_, path) =>
      {
        if (path.endsWith('/ended-couples'))
        {
          return [{ id: endedCouple.id, endedAt: '2026-11-13T10:00:00Z' }];
        }

        return path.endsWith('/data-deletion') ? noContent() : undefined;
      }),
    });

    expect(await screen.findByRole('heading', { name: 'Percorsi conclusi di «Dott.ssa Bianchi»' })).toBeTruthy();
    expect(screen.getByText('Percorso concluso il 13 novembre 2026')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Elimina i dati' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(DELETE_DATA_TEXT)).toBeTruthy();
    await user.type(within(dialog).getByLabelText('La tua password'), 'la mia password lunga');
    await user.click(within(dialog).getByRole('button', { name: 'Elimina i dati' }));

    await waitFor(() => expect(server.paths()).toContain(`POST /api/couples/${endedCouple.id}/data-deletion`));
  });

  it('reads as not found for an active therapist page, and for anyone but the administrator', async () =>
  {
    renderApp({ path: `/amministrazione/terapeuti/${active.id}`, account: administrator, answer: answers() });
    expect(await screen.findByRole('heading', { name: 'Pagina non trovata' })).toBeTruthy();
  });

  it('is not found by a patient', async () =>
  {
    const { server } = renderApp({ path: '/amministrazione', account: patient });

    expect(await screen.findByRole('heading', { name: 'Pagina non trovata' })).toBeTruthy();
    expect(server.paths()).not.toContain('GET /api/admin/therapists');
  });
});
