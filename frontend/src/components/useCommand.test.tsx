import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/errors';
import { ERROR_TEXTS } from '../texts/errors';
import { ErrorText } from './ErrorText';
import { useCommand } from './useCommand';

function Command({ action }: { action: () => Promise<unknown> })
{
  const command = useCommand(action);

  return (
    <>
      <button type="button" onClick={() => void command.run()} disabled={command.pending}>
        Invia
      </button>
      {/* Two starts before the page updates: a form submitted twice by the keyboard, for instance. */}
      <button
        type="button"
        onClick={() =>
        {
          void command.run();
          void command.run();
        }}
      >
        Invia due volte
      </button>
      <ErrorText error={command.error} />
    </>
  );
}

/** A page whose loader counts how many times the data on screen were read. */
function renderCommand(action: () => Promise<unknown>)
{
  const loader = vi.fn(() => ({}));
  const router = createMemoryRouter([{ path: '/', loader, element: <Command action={action} />, HydrateFallback: () => null }]);
  render(<RouterProvider router={router} />);

  return { loader, user: userEvent.setup() };
}

describe('a command', () =>
{
  it('disables its button while in flight, so a second click does nothing', async () =>
  {
    let finish: () => void = () => undefined;
    const action = vi.fn(() => new Promise<void>((done) =>
    {
      finish = done;
    }));
    const { user } = renderCommand(action);
    const button = await screen.findByRole('button', { name: 'Invia' });

    await user.click(button);
    expect(button).toHaveProperty('disabled', true);
    await user.click(button);
    finish();

    await waitFor(() => expect(button).toHaveProperty('disabled', false));
    expect(action).toHaveBeenCalledOnce();
  });

  it('runs once even when started twice before the page updates', async () =>
  {
    const action = vi.fn(async () => undefined);
    const { user } = renderCommand(action);

    await user.click(await screen.findByRole('button', { name: 'Invia due volte' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Invia' })).toHaveProperty('disabled', false));
    expect(action).toHaveBeenCalledOnce();
  });

  it('re-reads the data on screen after a success', async () =>
  {
    const { user, loader } = renderCommand(async () => undefined);

    await user.click(await screen.findByRole('button', { name: 'Invia' }));

    await waitFor(() => expect(loader).toHaveBeenCalledTimes(2));
  });

  it('shows why a rule refused it, and re-reads the data, since the other partner may have acted', async () =>
  {
    const { user, loader } = renderCommand(() => Promise.reject(ApiError.fromAnswer(409, { code: 'NOT_YOUR_TURN' })));

    await user.click(await screen.findByRole('button', { name: 'Invia' }));

    expect(await screen.findByText(ERROR_TEXTS.NOT_YOUR_TURN)).toBeTruthy();
    await waitFor(() => expect(loader).toHaveBeenCalledTimes(2));
  });

  it('only shows other refusals', async () =>
  {
    const { user, loader } = renderCommand(() => Promise.reject(ApiError.fromAnswer(404, { code: 'NOT_FOUND' })));

    await user.click(await screen.findByRole('button', { name: 'Invia' }));

    expect(await screen.findByText(ERROR_TEXTS.NOT_FOUND)).toBeTruthy();
    expect(loader).toHaveBeenCalledOnce();
  });
});
