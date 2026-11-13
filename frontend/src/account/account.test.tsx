import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { activeCouple, endedCouple, noContent, patient, therapist } from '../api/testing';
import { renderApp } from '../app/testing';
import { ERROR_TEXTS } from '../texts/errors';
import { DELETE_ACCOUNT_TEXT, PASSWORD_LINK_SENT } from './AccountPage';

describe('the account page', () =>
{
  it('shows the account and sends the link to change the password', async () =>
  {
    const { user, server } = renderApp({
      path: '/account',
      account: therapist,
      answer: (_, path) => (path === 'POST /api/auth/password-reset' ? new Response(null, { status: 202 }) : undefined),
    });

    expect(await screen.findByText('terapia@example.test')).toBeTruthy();
    expect(screen.getByText('Terapeuta')).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Elimina l’account' }), 'only a patient deletes the account').toBeNull();
    await user.click(screen.getByRole('button', { name: 'Inviami il link' }));

    expect(await screen.findByText(PASSWORD_LINK_SENT)).toBeTruthy();
    expect(await server.requests.at(-1)?.json()).toEqual({ email: therapist.email });
  });

  it('is reached from the menu', async () =>
  {
    const { user, router } = renderApp({ account: therapist });

    await user.click(await screen.findByRole('button', { name: 'Dott. Rossi' }));
    await user.click(await screen.findByRole('button', { name: 'Il mio account' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/account'));
  });

  it('lets a patient outside an active couple delete the account, then goes to the login', async () =>
  {
    const { user, server, fake } = renderApp({
      path: '/account',
      account: patient,
      answer: (_, path) =>
      {
        if (path === 'GET /api/me/couples')
        {
          return [endedCouple];
        }

        return path === 'POST /api/me/account-deletion' ? noContent() : undefined;
      },
    });

    await user.click(await screen.findByRole('button', { name: 'Elimina l’account' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(DELETE_ACCOUNT_TEXT)).toBeTruthy();
    await user.type(within(dialog).getByLabelText('La tua password'), 'la mia password lunga');
    await user.click(within(dialog).getByRole('button', { name: 'Elimina l’account' }));

    await waitFor(() => expect(fake.deps.reloadAt).toHaveBeenCalledWith('/accedi', { reason: 'deleted' }));
    expect(fake.posted).toContain('logout');
    expect(server.paths()).toContain('POST /api/me/account-deletion');
  });

  it('explains why a patient in an active couple can not delete the account', async () =>
  {
    renderApp({ path: '/account', account: patient, answer: (_, path) => (path === 'GET /api/me/couples' ? [activeCouple] : undefined) });

    expect(await screen.findByText(ERROR_TEXTS.ACCOUNT_IN_ACTIVE_COUPLE)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Elimina l’account' })).toBeNull();
  });
});
