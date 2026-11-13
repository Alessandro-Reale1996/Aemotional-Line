import { useRef, useState } from 'react';
import { useRevalidator } from 'react-router';

import { ApiError } from '../api/errors';

export interface CommandOptions<R>
{
  /** Called with the result when the command succeeds. */
  onDone?: (result: R) => void;
  /** Re-read the data on screen after a success (true by default). */
  reread?: boolean;
}

/**
 * One action of the user that calls the API (CLAUDE.md, frontend points 2 and 4). `pending` disables its button while the
 * request is in flight (a second click does nothing); a failure is kept in `error`, shown next to the button, and what was
 * written stays. A success re-reads everything on screen, and so does a refusal by a rule (409): the other partner may
 * have acted in the meantime, and there are no live updates. `error` may also be a text the form sets itself, when it
 * refuses to send a blank or too long field.
 */
export function useCommand<A extends unknown[], R>(action: (...args: A) => Promise<R>, options: CommandOptions<R> = {})
{
  const revalidator = useRevalidator();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<ApiError | string>();
  const inFlight = useRef(false);
  const { onDone, reread = true } = options;

  async function run(...args: A): Promise<void>
  {
    if (inFlight.current)
    {
      return;
    }

    inFlight.current = true;
    setPending(true);
    setError(undefined);

    try
    {
      const result = await action(...args);
      onDone?.(result);

      if (reread)
      {
        void revalidator.revalidate();
      }
    }
    catch (failure)
    {
      const refused = ApiError.from(failure);
      setError(refused);

      if (refused.status === 409)
      {
        void revalidator.revalidate();
      }
    }
    finally
    {
      inFlight.current = false;
      setPending(false);
    }
  }

  return { run, pending, error, setError };
}
