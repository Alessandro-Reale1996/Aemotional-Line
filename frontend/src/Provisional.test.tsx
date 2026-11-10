import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { createApi } from './api/client';
import { fakeServer, limits } from './api/testing';
import { Provisional } from './Provisional';

describe('the stand-in page', () =>
{
  it('shows a limit read from the API, and opens its menu and its dialog', async () =>
  {
    const user = userEvent.setup();
    render(<Provisional api={createApi(fakeServer(() => limits).send)} />);

    expect(await screen.findByText('Un messaggio può avere fino a 20 paragrafi.')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Menu di prova' }));
    await user.click(await screen.findByRole('button', { name: 'Apri la finestra' }));

    expect(await screen.findByRole('dialog')).toBeTruthy();
    expect(screen.getByText('Finestra di prova')).toBeTruthy();
  });

  it('says so when the server does not answer', async () =>
  {
    render(<Provisional api={createApi(() => Promise.reject(new TypeError('offline')))} />);

    expect(await screen.findByText('Il server non risponde, riprova tra qualche minuto.')).toBeTruthy();
  });
});
