import createClient, { type Middleware } from 'openapi-fetch';

import { ApiError } from './errors';
import type { components, paths } from './schema';

export type Limits = components['schemas']['LimitsView'];
export type Account = components['schemas']['AccountView'];
export type Role = Account['role'];
export type Person = components['schemas']['PersonView'];
export type Couple = components['schemas']['CoupleView'];
export type Summary = components['schemas']['SummaryView'];
export type CoupleSummary = components['schemas']['CoupleSummary'];
export type Invitation = components['schemas']['InvitationView'];
export type Therapist = components['schemas']['TherapistView'];
export type EndedCouple = components['schemas']['EndedCoupleView'];

/** The cookie the server keeps the CSRF token in; the app reads it and sends it back in a header. */
export const CSRF_COOKIE = '__Host-XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const READS = ['GET', 'HEAD', 'OPTIONS'];

export type Send = (request: Request) => Promise<Response>;

export interface ApiOptions
{
  /** How requests reach the server: the browser's fetch, or a fake one in the tests. */
  send?: Send;
  /** Reads the CSRF token; from the cookie by default. */
  csrfToken?: () => string | undefined;
  /** Told of every request the app makes (src/session counts them for the session's keepalive). */
  onRequest?: () => void;
  /** Told when an answer says the session is over (401 AUTHENTICATION_REQUIRED). */
  onSessionLost?: () => void;
}

/**
 * The client of the API (CLAUDE.md, frontend points 2 and 3). Its paths, bodies and answers are typed by openapi.json.
 * Every POST, PUT and DELETE carries the CSRF token, read from its cookie each time (the login replaces it); when the
 * cookie is missing, GET /api/auth/csrf sets it first. A 401 AUTHENTICATION_REQUIRED tells the session. Use it through
 * {@link ask}, which turns every failure into an {@link ApiError}.
 */
export function createApi(options: ApiOptions = {})
{
  const send = options.send ?? ((request: Request) => fetch(request));
  const csrfToken = options.csrfToken ?? readCsrfCookie;
  const origin = window.location.origin;

  async function csrfHeader(): Promise<string>
  {
    let token = csrfToken();

    if (!token)
    {
      await send(new Request(`${origin}/api/auth/csrf`));
      token = csrfToken();
    }

    return token ?? '';
  }

  const session: Middleware = {
    async onRequest({ request })
    {
      options.onRequest?.();

      if (!READS.includes(request.method))
      {
        request.headers.set(CSRF_HEADER, await csrfHeader());
      }

      return request;
    },
    async onResponse({ response })
    {
      if (response.status === 401)
      {
        const error = await ApiError.fromResponse(response.clone());

        if (error.code === 'AUTHENTICATION_REQUIRED')
        {
          options.onSessionLost?.();
        }
      }

      return undefined;
    },
  };

  const client = createClient<paths>({ baseUrl: origin, fetch: (request: Request) => send(request) });
  client.use(session);

  return Object.assign(client, {
    /**
     * Whose session this browser holds, or null without one. It goes around the middleware: here a 401 only means
     * "nobody", it isn't news for the session.
     */
    async whoIsLoggedIn(): Promise<Account | null>
    {
      let response: Response;

      try
      {
        response = await send(new Request(`${origin}/api/auth/me`));
      }
      catch (failure)
      {
        throw ApiError.from(failure);
      }

      if (response.status === 401)
      {
        return null;
      }

      if (!response.ok)
      {
        throw await ApiError.fromResponse(response);
      }

      return (await response.json()) as Account;
    },

    /** The logout: the one call the spec doesn't describe, since a security filter answers it (204). */
    async logOut(): Promise<void>
    {
      let response: Response;

      try
      {
        response = await send(new Request(`${origin}/api/auth/logout`, { method: 'POST', headers: { [CSRF_HEADER]: await csrfHeader() } }));
      }
      catch (failure)
      {
        throw ApiError.from(failure);
      }

      if (!response.ok)
      {
        throw await ApiError.fromResponse(response);
      }
    },
  });
}

export type Api = ReturnType<typeof createApi>;

/**
 * The data of a call that succeeded; otherwise an {@link ApiError}, also when the server can't be reached. What every
 * screen does with the client: `const couples = await ask(api.GET('/api/me/couples'))`.
 */
export async function ask<T>(call: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T>
{
  let result: { data?: T; error?: unknown; response: Response };

  try
  {
    result = await call;
  }
  catch (failure)
  {
    throw ApiError.from(failure);
  }

  if (!result.response.ok)
  {
    throw ApiError.fromAnswer(result.response.status, result.error);
  }

  return result.data as T;
}

function readCsrfCookie(): string | undefined
{
  const prefix = `${CSRF_COOKIE}=`;

  return document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(prefix))
    ?.slice(prefix.length);
}
