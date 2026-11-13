import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { activeCouple, endedCouple, noContent, patient, problem, therapist } from '../api/testing';
import { renderApp } from '../app/testing';
import { INVITATION_SENT } from '../invitations/NewInvitationPage';
import { LEAVE_WITH_TEXT } from '../session/notices';
import { discardUnsentText } from '../session/unsent';
import { ERROR_TEXTS } from '../texts/errors';
import { DATA_DELETED, DELETE_DATA_TEXT, END_TEXT } from './CouplePage';

const PASSWORD = 'la mia password lunga';

afterEach(() => discardUnsentText());

describe('the couple page', () =>
{
  it('lets the therapist end the couple with the password, after saying what happens', async () =>
  {
    let couple = activeCouple;
    const { user, server } = renderApp({
      path: `/coppie/${activeCouple.id}`,
      account: therapist,
      answer: (request, path) =>
      {
        if (path === 'GET /api/me/couples')
        {
          return [couple];
        }

        if (path === `POST /api/couples/${activeCouple.id}/end`)
        {
          return request.json().then((body: { password: string }) =>
          {
            if (body.password !== PASSWORD)
            {
              return problem(400, 'INCORRECT_PASSWORD');
            }

            couple = { ...activeCouple, active: false, endedAt: '2026-11-14T09:00:00Z' };

            return noContent();
          });
        }

        return undefined;
      },
    });

    expect(await screen.findByRole('heading', { name: 'Alex e Sam' })).toBeTruthy();
    expect(screen.getByText('Percorso in corso')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Concludi il percorso' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(END_TEXT)).toBeTruthy();

    await user.type(within(dialog).getByLabelText('La tua password'), 'sbagliata');
    await user.click(within(dialog).getByRole('button', { name: 'Concludi il percorso' }));
    expect(await within(dialog).findByText(ERROR_TEXTS.INCORRECT_PASSWORD)).toBeTruthy();

    await user.clear(within(dialog).getByLabelText('La tua password'));
    await user.type(within(dialog).getByLabelText('La tua password'), PASSWORD);
    await user.click(within(dialog).getByRole('button', { name: 'Concludi il percorso' }));

    expect(await screen.findByText('Percorso concluso il 14 novembre 2026')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Elimina i dati' })).toBeTruthy();
    expect(server.paths().filter((path) => path.endsWith('/end'))).toHaveLength(2);
  });

  it('deletes the data of an ended couple, then goes home with a notice', async () =>
  {
    const { user, router } = renderApp({
      path: `/coppie/${endedCouple.id}`,
      account: therapist,
      answer: (_, path) =>
      {
        if (path === 'GET /api/me/couples')
        {
          return router.state.location.pathname === '/' ? [] : [endedCouple];
        }

        return path.endsWith('/data-deletion') ? noContent() : undefined;
      },
    });

    await user.click(await screen.findByRole('button', { name: 'Elimina i dati' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(DELETE_DATA_TEXT)).toBeTruthy();
    await user.type(within(dialog).getByLabelText('La tua password'), PASSWORD);
    await user.click(within(dialog).getByRole('button', { name: 'Elimina i dati' }));

    expect(await screen.findByText(DATA_DELETED)).toBeTruthy();
    expect(router.state.location.pathname).toBe('/');
  });

  it('offers nothing irreversible to a partner', async () =>
  {
    renderApp({ path: `/coppie/${activeCouple.id}`, account: patient, answer: (_, path) => (path === 'GET /api/me/couples' ? [activeCouple] : undefined) });

    expect(await screen.findByRole('heading', { name: 'Alex e Sam' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Concludi il percorso' })).toBeNull();
  });

  it('reads as not found when the couple is not the user own', async () =>
  {
    renderApp({ path: `/coppie/${activeCouple.id}`, account: patient });

    expect(await screen.findByRole('heading', { name: 'Pagina non trovata' })).toBeTruthy();
    expect(screen.getByText(ERROR_TEXTS.NOT_FOUND)).toBeTruthy();
  });
});

describe('the invitation of a couple', () =>
{
  const fill = async (user: ReturnType<typeof renderApp>['user'], values: string[]) =>
  {
    const [nameOne, emailOne, nameTwo, emailTwo] = values;
    const fields = await screen.findAllByLabelText(/^(Nome|Email)$/);

    for (const [field, value] of [[0, nameOne], [1, emailOne], [2, nameTwo], [3, emailTwo]] as const)
    {
      if (value)
      {
        await user.type(fields[field] as HTMLElement, value);
      }
    }
  };

  it('names the missing field, and refuses the same email twice, before sending', async () =>
  {
    const { user, server } = renderApp({ path: '/inviti/nuovo', account: therapist });

    await fill(user, ['Alex', 'alex@example.test', 'Sam', '']);
    await user.click(screen.getByRole('button', { name: 'Invia l’invito' }));
    expect(await screen.findByText('Manca l’email della seconda persona.')).toBeTruthy();

    await user.type(screen.getAllByLabelText('Email')[1] as HTMLElement, 'ALEX@example.test ');
    await user.click(screen.getByRole('button', { name: 'Invia l’invito' }));
    expect(await screen.findByText(ERROR_TEXTS.SAME_EMAIL_TWICE)).toBeTruthy();
    expect(server.paths()).not.toContain('POST /api/invitations');
  });

  it('sends the two people, then goes home with a notice', async () =>
  {
    const { user, server, router } = renderApp({
      path: '/inviti/nuovo',
      account: therapist,
      answer: (_, path) => (path === 'POST /api/invitations' ? Response.json({ id: activeCouple.id }, { status: 201 }) : undefined),
    });

    await fill(user, ['Alex', 'alex@example.test', 'Sam', 'sam@example.test']);
    await user.click(screen.getByRole('button', { name: 'Invia l’invito' }));

    expect(await screen.findByText(INVITATION_SENT)).toBeTruthy();
    expect(router.state.location.pathname).toBe('/');
    expect(screen.queryByText(LEAVE_WITH_TEXT), 'the text was sent: nothing to confirm').toBeNull();
    expect(await server.requests.find((request) => request.method === 'POST')?.json()).toEqual({
      nameOne: 'Alex', emailOne: 'alex@example.test', nameTwo: 'Sam', emailTwo: 'sam@example.test',
    });
  });

  it('protects what was written', async () =>
  {
    const { user } = renderApp({ path: '/inviti/nuovo', account: therapist });

    await fill(user, ['Alex']);
    await user.click(screen.getByRole('link', { name: 'Coppie' }));

    expect(await screen.findByText(LEAVE_WITH_TEXT)).toBeTruthy();
  });

  it('reads as not found for a patient', async () =>
  {
    renderApp({ path: '/inviti/nuovo', account: patient });

    expect(await screen.findByRole('heading', { name: 'Pagina non trovata' })).toBeTruthy();
  });
});
