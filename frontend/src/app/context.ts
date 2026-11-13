import { createContext, use, useSyncExternalStore } from 'react';
import { useRouteLoaderData } from 'react-router';

import type { Account, Api } from '../api/client';
import type { EndReason } from '../session/navigation';
import type { Session } from '../session/session';

export interface App
{
  api: Api;
  session: Session;
}

/** The API client and the session, given to every screen; the tests give fakes. */
export const AppContext = createContext<App | null>(null);

export function useApp(): App
{
  const app = use(AppContext);

  if (!app)
  {
    throw new Error('useApp is used outside AppContext.');
  }

  return app;
}

/** What the root loader gives: the account logged in, or null. */
export interface RootData
{
  account: Account | null;
}

/** The account logged in, read once per page load by the root route (a login or a logout reloads the page). */
export function useAccount(): Account | null
{
  return (useRouteLoaderData('root') as RootData | undefined)?.account ?? null;
}

/** Why the page is hidden behind the login, or null (session.ts). */
export function useLock(): EndReason | null
{
  const { session } = useApp();

  return useSyncExternalStore(session.subscribe, session.lock);
}
