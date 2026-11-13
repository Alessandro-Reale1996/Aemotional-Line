import { act, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { Link, type RouteObject } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { patient, therapist } from '../api/testing';
import { EXIT_WITH_TEXT, LEAVE_WITH_TEXT, OVERLAY_NOTICE } from '../session/notices';
import { discardUnsentText, useUnsentText } from '../session/unsent';
import { ERROR_TEXTS, UNREACHABLE } from '../texts/errors';
import type { App, RootData } from './context';
import { AppLayout, ErrorPage, Root } from './Root';
import { renderApp } from './testing';

afterEach(() => discardUnsentText());

/** A page with a text field, standing in for the forms of the next steps. */
function Draft()
{
  const [text, setText] = useState('');
  useUnsentText(text !== '');

  return (
    <>
      <label htmlFor="draft">Bozza</label>
      <textarea id="draft" value={text} onChange={(event) => setText(event.target.value)} autoComplete="off" />
      <Link to="/altro">Un’altra pagina</Link>
    </>
  );
}

function withDraft({ api, session }: App): RouteObject[]
{
  return [
    {
      id: 'root',
      path: '/',
      loader: async (): Promise<RootData> =>
      {
        const account = await api.whoIsLoggedIn();
        session.setAccount(account);

        return { account };
      },
      element: <Root />,
      errorElement: <ErrorPage />,
      children: [
        {
          element: <AppLayout />,
          children: [
            { index: true, element: <Draft /> },
            { path: 'altro', element: <p>L’altra pagina</p> },
          ],
        },
      ],
    },
  ];
}

describe('the frame of a logged-in user', () =>
{
  it('shows the name and the role, and logs out', async () =>
  {
    const { user, fake } = renderApp({ account: therapist });

    expect(await screen.findByRole('heading', { name: 'Coppie' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Menu' }), 'the toggle of the bar on a phone').toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Dott. Rossi' }));
    expect(await screen.findByText('Terapeuta')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Esci' }));

    await waitFor(() => expect(fake.deps.reloadAt).toHaveBeenCalledWith('/accedi', { reason: 'logout' }));
  });

  it('sends whoever has no session to the login, which comes back to the page', async () =>
  {
    const { router } = renderApp({ path: '/' });

    expect(await screen.findByRole('heading', { name: 'Accedi' })).toBeTruthy();
    expect(router.state.location.pathname).toBe('/accedi');
    expect(router.state.location.state).toEqual({ returnTo: '/' });
  });

  it('says that an unknown address does not exist, with or without a session', async () =>
  {
    renderApp({ path: '/conversazioni/42/inesistente', account: patient });

    expect(await screen.findByRole('heading', { name: 'Pagina non trovata' })).toBeTruthy();
    expect(screen.getByText(ERROR_TEXTS.NOT_FOUND)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Alex' })).toBeTruthy();
  });

  it('says that the server does not answer when the page can not be read', async () =>
  {
    renderApp({ answer: () => Promise.reject(new TypeError('offline')) });

    expect(await screen.findByText(UNREACHABLE)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Riprova' })).toBeTruthy();
  });
});

describe('unsent text', () =>
{
  it('asks before leaving the page through a link', async () =>
  {
    const { user } = renderApp({ account: patient, routes: withDraft });

    await user.type(await screen.findByLabelText('Bozza'), 'Quello che volevo dire');
    await user.click(screen.getByRole('link', { name: 'Un’altra pagina' }));
    expect(await screen.findByText(LEAVE_WITH_TEXT)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Resta' }));
    await waitFor(() => expect(screen.queryByText(LEAVE_WITH_TEXT)).toBeNull());
    expect(screen.getByLabelText('Bozza')).toHaveProperty('value', 'Quello che volevo dire');

    await user.click(screen.getByRole('link', { name: 'Un’altra pagina' }));
    await user.click(await screen.findByRole('button', { name: 'Lascia la pagina' }));
    expect(await screen.findByText('L’altra pagina')).toBeTruthy();
  });

  it('asks before logging out', async () =>
  {
    const { user, fake } = renderApp({ account: patient, routes: withDraft });

    await user.type(await screen.findByLabelText('Bozza'), 'Quasi finito');
    await user.click(screen.getByRole('button', { name: 'Alex' }));
    await user.click(await screen.findByRole('button', { name: 'Esci' }));
    expect(await screen.findByText(EXIT_WITH_TEXT)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Annulla' }));

    expect(fake.deps.logOut).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Bozza')).toHaveProperty('value', 'Quasi finito');
  });

  it('stays behind the login when the session is lost, and comes back with the same account', async () =>
  {
    const { user, session, fake } = renderApp({
      account: patient,
      routes: withDraft,
      answer: (_, path) => (path === 'POST /api/auth/login' ? patient : undefined),
    });

    await user.type(await screen.findByLabelText('Bozza'), 'Una lettera lunga');
    act(() => session.sessionLost());

    expect(await screen.findByText(OVERLAY_NOTICE)).toBeTruthy();
    expect(screen.queryByRole('textbox', { name: 'Bozza' }), 'the page is hidden').toBeNull();
    expect(screen.getByLabelText('Email')).toHaveProperty('value', patient.email);
    await user.type(screen.getByLabelText('Password'), 'la mia password lunga');
    await user.click(screen.getByRole('button', { name: 'Accedi' }));

    await waitFor(() => expect(screen.queryByText(OVERLAY_NOTICE)).toBeNull());
    expect(screen.getByLabelText('Bozza')).toHaveProperty('value', 'Una lettera lunga');
    expect(fake.deps.reloadAt).not.toHaveBeenCalled();
    expect(fake.posted).toContain('login');
  });

  it('is lost, after saying so, when another account logs in over the page', async () =>
  {
    const { user, session, fake } = renderApp({
      account: patient,
      routes: withDraft,
      answer: (_, path) => (path === 'POST /api/auth/login' ? therapist : undefined),
    });

    await user.type(await screen.findByLabelText('Bozza'), 'Una lettera lunga');
    act(() => session.sessionLost());
    await user.clear(await screen.findByLabelText('Email'));
    await user.type(screen.getByLabelText('Email'), therapist.email);
    await user.type(screen.getByLabelText('Password'), 'un’altra password lunga');
    await user.click(screen.getByRole('button', { name: 'Accedi' }));

    await waitFor(() => expect(fake.deps.reloadAt).toHaveBeenCalledWith('/', undefined));
  });
});
