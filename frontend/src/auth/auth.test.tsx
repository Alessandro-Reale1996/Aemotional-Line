import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { noContent, patient, problem, therapist } from '../api/testing';
import { renderApp } from '../app/testing';
import { END_NOTICES, SHARED_DEVICE } from '../session/notices';
import { ERROR_TEXTS } from '../texts/errors';
import { RESET_REQUESTED } from './ForgottenPasswordPage';
import { NO_CODE } from './NewPasswordPage';
import { MISSING_EMAIL } from './passwords';

const PASSWORD = 'una frase lunga e tranquilla';

describe('the login page', () =>
{
  it('says why it is shown, and goes back to the page afterwards', async () =>
  {
    const { user, fake, server } = renderApp({
      path: '/accedi',
      state: { reason: 'inactivity', returnTo: '/conversazioni/42' },
      answer: (_, path) => (path === 'POST /api/auth/login' ? patient : undefined),
    });

    expect(await screen.findByText(END_NOTICES.inactivity)).toBeTruthy();
    expect(screen.getByText(SHARED_DEVICE)).toBeTruthy();
    await user.type(screen.getByLabelText('Email'), patient.email);
    await user.type(screen.getByLabelText('Password'), PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Accedi' }));

    await waitFor(() => expect(fake.deps.reloadAt).toHaveBeenCalledWith('/conversazioni/42', undefined));
    expect(fake.posted).toContain('login');
    expect(await server.requests.find((request) => request.url.endsWith('/api/auth/login'))?.json()).toEqual({ email: patient.email, password: PASSWORD });
  });

  it('reads the same whatever the reason of a refusal, and keeps what was written', async () =>
  {
    const { user } = renderApp({ path: '/accedi', answer: (_, path) => (path === 'POST /api/auth/login' ? problem(401, 'INVALID_CREDENTIALS') : undefined) });

    await user.type(await screen.findByLabelText('Email'), patient.email);
    await user.type(screen.getByLabelText('Password'), 'sbagliata');
    await user.click(screen.getByRole('button', { name: 'Accedi' }));

    expect(await screen.findByRole('alert')).toHaveProperty('textContent', ERROR_TEXTS.INVALID_CREDENTIALS);
    expect(screen.getByLabelText('Email')).toHaveProperty('value', patient.email);
  });

  it('names the missing field without asking the server', async () =>
  {
    const { user, server } = renderApp({ path: '/accedi' });

    await user.click(await screen.findByRole('button', { name: 'Accedi' }));

    expect(await screen.findByText(MISSING_EMAIL)).toBeTruthy();
    expect(server.paths()).toEqual(['GET /api/auth/me']);
  });

  it('says whose session is open, instead of going into it', async () =>
  {
    const { user, fake } = renderApp({ path: '/accedi', account: therapist });

    expect(await screen.findByText('Hai già effettuato l’accesso con l’account di «Dott. Rossi».')).toBeTruthy();
    expect(screen.queryByLabelText('Password')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Esci' }));

    await waitFor(() => expect(fake.deps.reloadAt).toHaveBeenCalledWith('/accedi', { reason: 'logout' }));
    expect(fake.deps.logOut).toHaveBeenCalledOnce();
  });

  it('shows the password only when asked', async () =>
  {
    const { user } = renderApp({ path: '/accedi' });
    const field = await screen.findByLabelText('Password');

    expect(field).toHaveProperty('type', 'password');
    await user.click(screen.getByRole('button', { name: 'Mostra la password' }));
    expect(field).toHaveProperty('type', 'text');
    await user.click(screen.getByRole('button', { name: 'Nascondi la password' }));
    expect(field).toHaveProperty('type', 'password');
  });
});

describe('the forgotten password', () =>
{
  it('sends the email and answers the same for any address', async () =>
  {
    const { user, server } = renderApp({
      path: '/password-dimenticata',
      answer: (_, path) => (path === 'POST /api/auth/password-reset' ? new Response(null, { status: 202 }) : undefined),
    });

    await user.type(await screen.findByLabelText('Email'), 'chiunque@example.test');
    await user.click(screen.getByRole('button', { name: 'Invia il link' }));

    expect(await screen.findByText(RESET_REQUESTED)).toBeTruthy();
    expect(await server.requests.at(-1)?.json()).toEqual({ email: 'chiunque@example.test' });
  });
});

describe('the pages opened from an email', () =>
{
  it('take the code out of the address at once, then activate the account with the new password', async () =>
  {
    const { user, server, router } = renderApp({
      path: '/attiva?codice=abc-123',
      answer: (_, path) => (path === 'POST /api/auth/activate' ? noContent() : undefined),
    });

    await screen.findByRole('heading', { name: 'Attiva l’account' });
    await waitFor(() => expect(router.state.location.search).toBe(''));
    expect(screen.getByText(/^Da 12 a 64 caratteri/)).toBeTruthy();
    await user.type(screen.getByLabelText('Password'), PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Attiva l’account' }));

    expect(await screen.findByText('L’account è attivo. Ora puoi accedere.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Accedi' })).toHaveProperty('pathname', '/accedi');
    expect(await server.requests.at(-1)?.json()).toEqual({ code: 'abc-123', password: PASSWORD });
  });

  it('refuse a password too short before sending it', async () =>
  {
    const { user, server } = renderApp({ path: '/attiva?codice=abc' });

    await user.type(await screen.findByLabelText('Password'), 'corta');
    await user.click(screen.getByRole('button', { name: 'Attiva l’account' }));

    expect(await screen.findByText(ERROR_TEXTS.PASSWORD_TOO_SHORT)).toBeTruthy();
    expect(server.paths()).not.toContain('POST /api/auth/activate');
  });

  it('say how to get a new link when the code is refused', async () =>
  {
    const { user } = renderApp({
      path: '/reimposta-password?codice=old',
      answer: (_, path) => (path === 'POST /api/auth/password-reset/confirm' ? problem(400, 'INVALID_LINK') : undefined),
    });

    await user.type(await screen.findByLabelText('Password'), PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Salva la nuova password' }));

    expect(await screen.findByText(`${ERROR_TEXTS.INVALID_LINK} Puoi chiederne uno nuovo da «Password dimenticata».`)).toBeTruthy();
    expect(screen.queryByLabelText('Password')).toBeNull();
  });

  it('ask to open the link again when the address has no code', async () =>
  {
    renderApp({ path: '/attiva' });

    expect(await screen.findByText(NO_CODE)).toBeTruthy();
  });

  it('offer to leave another account session before logging in', async () =>
  {
    const { user, fake } = renderApp({
      path: '/reimposta-password?codice=abc',
      account: patient,
      answer: (_, path) => (path === 'POST /api/auth/password-reset/confirm' ? noContent() : undefined),
    });

    await user.type(await screen.findByLabelText('Password'), PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Salva la nuova password' }));
    expect(await screen.findByText('Su questo dispositivo è aperto l’account di «Alex».')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Esci e accedi' }));

    await waitFor(() => expect(fake.deps.reloadAt).toHaveBeenCalledWith('/accedi', { reason: 'logout' }));
  });
});
