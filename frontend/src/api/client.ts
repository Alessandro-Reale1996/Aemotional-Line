import createClient from 'openapi-fetch';

import type { components, paths } from './schema';

/**
 * The typed client of the API (CLAUDE.md, frontend point 2): its paths, bodies and answers come from openapi.json. F4 adds
 * the CSRF header and turns errors into ApiError. Tests pass a fake fetch instead of the browser's.
 */
export type Api = ReturnType<typeof createApi>;

export type Limits = components['schemas']['LimitsView'];

export function createApi(send: (request: Request) => Promise<Response> = (request) => fetch(request)) {
  return createClient<paths>({ baseUrl: window.location.origin, fetch: send });
}
