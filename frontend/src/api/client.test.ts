import { describe, expect, it, vi } from 'vitest';

import { ask, createApi } from './client';
import { ApiError } from './errors';
import { fakeServer, limits, noContent, patient, problem } from './testing';

describe('the client', () =>
{
  it('asks the API on the page own origin and reads its answer', async () =>
  {
    const server = fakeServer(() => limits);
    const api = createApi({ send: server.send });

    const answer = await ask(api.GET('/api/limits'));

    expect(answer.messageParagraphs).toBe(20);
    expect(server.paths()).toEqual(['GET /api/limits']);
    expect(new URL(server.requests[0]?.url ?? '').origin).toBe(window.location.origin);
  });

  it('sends the CSRF token, read from its cookie at every change, and none with a read', async () =>
  {
    let token = 'first';
    const server = fakeServer(() => noContent());
    const api = createApi({ send: server.send, csrfToken: () => token });

    await ask(api.POST('/api/auth/password-reset', { body: { email: 'a@example.test' } }));
    token = 'replaced by the login';
    await ask(api.POST('/api/invitations/{id}/accept', { params: { path: { id: patient.id } } }));
    await api.GET('/api/me/couples');

    expect(server.requests.map((request) => request.headers.get('X-XSRF-TOKEN'))).toEqual(['first', 'replaced by the login', null]);
  });

  it('asks for the CSRF cookie first when it is missing', async () =>
  {
    let token: string | undefined;
    const server = fakeServer((request) =>
    {
      if (new URL(request.url).pathname === '/api/auth/csrf')
      {
        token = 'set by the server';
      }

      return noContent();
    });
    const api = createApi({ send: server.send, csrfToken: () => token });

    await ask(api.POST('/api/auth/password-reset', { body: { email: 'a@example.test' } }));

    expect(server.paths()).toEqual(['GET /api/auth/csrf', 'POST /api/auth/password-reset']);
    expect(server.requests[1]?.headers.get('X-XSRF-TOKEN')).toBe('set by the server');
  });

  it('tells of every request, and of a session that is over, but not of a failed login', async () =>
  {
    const onRequest = vi.fn();
    const onSessionLost = vi.fn();
    const server = fakeServer((request) =>
      new URL(request.url).pathname === '/api/auth/login' ? problem(401, 'INVALID_CREDENTIALS') : problem(401, 'AUTHENTICATION_REQUIRED'));
    const api = createApi({ send: server.send, csrfToken: () => 't', onRequest, onSessionLost });

    await expect(ask(api.POST('/api/auth/login', { body: { email: 'a@example.test', password: 'x' } }))).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' });
    expect(onSessionLost).not.toHaveBeenCalled();

    await expect(ask(api.GET('/api/me/couples'))).rejects.toMatchObject({ code: 'AUTHENTICATION_REQUIRED' });
    expect(onSessionLost).toHaveBeenCalledOnce();
    expect(onRequest).toHaveBeenCalledTimes(2);
  });

  it('says who is logged in, or nobody, without telling the session', async () =>
  {
    const onSessionLost = vi.fn();
    let answer: Response | object = patient;
    const api = createApi({ send: fakeServer(() => answer).send, onSessionLost });

    expect(await api.whoIsLoggedIn()).toEqual(patient);
    answer = problem(401, 'AUTHENTICATION_REQUIRED');
    expect(await api.whoIsLoggedIn()).toBeNull();
    expect(onSessionLost).not.toHaveBeenCalled();

    const offline = createApi({ send: () => Promise.reject(new TypeError('offline')) });
    await expect(offline.whoIsLoggedIn()).rejects.toMatchObject({ unreachable: true });
  });

  it('logs out with the CSRF token', async () =>
  {
    const server = fakeServer(() => noContent());
    const api = createApi({ send: server.send, csrfToken: () => 'token' });

    await api.logOut();

    expect(server.paths()).toEqual(['POST /api/auth/logout']);
    expect(server.requests[0]?.headers.get('X-XSRF-TOKEN')).toBe('token');
  });
});

describe('a failed call', () =>
{
  it('is an ApiError with the code the API gave', async () =>
  {
    const api = createApi({ send: fakeServer(() => problem(409, 'NOT_YOUR_TURN')).send, csrfToken: () => 't' });

    const failure = await ask(api.POST('/api/negotiations/{id}/send', { params: { path: { id: patient.id } }, body: { justification: 'x' } })).catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(ApiError);
    expect(failure).toMatchObject({ status: 409, code: 'NOT_YOUR_TURN', unreachable: false });
  });

  it('keeps the error id of an unexpected error', async () =>
  {
    const api = createApi({ send: fakeServer(() => problem(500, 'INTERNAL_ERROR', { errorId: 'e-42' })).send });

    await expect(ask(api.GET('/api/me/couples'))).rejects.toMatchObject({ code: 'INTERNAL_ERROR', errorId: 'e-42' });
  });

  it('means the server can not be reached when nothing answers, or something that is not the API', async () =>
  {
    const offline = createApi({ send: () => Promise.reject(new TypeError('Failed to fetch')) });
    const proxy = createApi({ send: async () => new Response('<html>Bad gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } }) });

    await expect(ask(offline.GET('/api/me/couples'))).rejects.toMatchObject({ unreachable: true });
    await expect(ask(proxy.GET('/api/me/couples'))).rejects.toMatchObject({ unreachable: true, status: 502 });
  });

  it('is not hidden when it is a mistake of the app', () =>
  {
    const mistake = new RangeError('a bug');

    expect(() => ApiError.from(mistake)).toThrow(mistake);
  });
});
