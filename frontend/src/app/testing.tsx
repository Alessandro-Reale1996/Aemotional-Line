import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, type RouteObject } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { type Account, createApi } from '../api/client';
import { emptySummary, fakeServer, limits, problem } from '../api/testing';
import { Session } from '../session/session';
import { fakeSessionDeps } from '../session/testing';
import { type App, AppContext } from './context';
import { createRoutes } from './routes';

interface Options
{
  /** The address opened, with its search. */
  path?: string;
  /** The location's state, as the session leaves it for the login page. */
  state?: unknown;
  /** Who is logged in: GET /api/auth/me answers with it, or 401. */
  account?: Account | null;
  /** Answers the requests it knows; undefined leaves the request to the defaults (me, limits, an empty summary, no couples). */
  answer?: (request: Request, path: string) => unknown;
  /** The pages; the app's own by default. */
  routes?: (app: App) => RouteObject[];
}

/**
 * Renders the app in memory (CLAUDE.md, frontend point 9): its pages and its session, on a fake server answering with data
 * typed by the spec. The session's own calls (logout, reloads) are spies the test can read.
 */
export function renderApp({ path = '/', state, account = null, answer = () => undefined, routes = createRoutes }: Options = {})
{
  const server = fakeServer((request) =>
  {
    const pathname = new URL(request.url).pathname;
    const given = answer(request, `${request.method} ${pathname}`);

    if (given !== undefined)
    {
      return given;
    }

    if (pathname === '/api/auth/me')
    {
      return account ?? problem(401, 'AUTHENTICATION_REQUIRED');
    }

    if (pathname === '/api/limits')
    {
      return limits;
    }

    if (pathname === '/api/me/summary')
    {
      return emptySummary;
    }

    if (pathname === '/api/me/couples')
    {
      return [];
    }

    throw new Error(`The test doesn't answer ${request.method} ${pathname}.`);
  });
  const fake = fakeSessionDeps(account);
  const session = new Session(fake.deps);
  const api = createApi({ send: server.send, csrfToken: () => 'token', onRequest: session.requestSent, onSessionLost: session.sessionLost });
  const app = { api, session };
  const [pathname, search = ''] = path.split('?');
  const router = createMemoryRouter(routes(app), { initialEntries: [{ pathname, search: search ? `?${search}` : '', state }] });
  const user = userEvent.setup();

  render(
    <AppContext value={app}>
      <RouterProvider router={router} />
    </AppContext>,
  );

  return { server, session, fake, router, user, api };
}
