import { vi } from 'vitest';

import type { Account } from '../api/client';
import type { SessionDeps } from './session';
import type { TabSignal } from './tabs';

/**
 * What a Session needs, faked for the tests: a clock moved by hand, a channel that records what this tab posts and lets
 * the test play the other tabs, and spies in place of the server and of the page reloads.
 */
export function fakeSessionDeps(whoIsLoggedIn: Account | null = null)
{
  let time = 1_000_000;
  let receive: ((signal: TabSignal) => void) | undefined;
  const posted: TabSignal[] = [];
  const deps = {
    whoIsLoggedIn: vi.fn<SessionDeps['whoIsLoggedIn']>(async () => whoIsLoggedIn),
    logOut: vi.fn<SessionDeps['logOut']>(async () => undefined),
    keepAlive: vi.fn<SessionDeps['keepAlive']>(async () => undefined),
    reloadAt: vi.fn<SessionDeps['reloadAt']>(),
    currentPath: () => '/conversazioni/42',
    now: () => time,
    channel: {
      post: (signal: TabSignal) => posted.push(signal),
      listen: (handler: (signal: TabSignal) => void) =>
      {
        receive = handler;

        return () =>
        {
          receive = undefined;
        };
      },
    },
  } satisfies SessionDeps;

  return {
    deps,
    posted,
    advance: (milliseconds: number) =>
    {
      time += milliseconds;
    },
    fromAnotherTab: (signal: TabSignal) => receive?.(signal),
  };
}

/** Lets pending promises settle (the session's calls to the fake server). */
export function settle(): Promise<void>
{
  return new Promise((done) => setTimeout(done, 0));
}
