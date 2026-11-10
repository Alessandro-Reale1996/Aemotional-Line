import { describe, expect, it } from 'vitest';

import { createApi } from './client';
import { fakeServer, limits } from './testing';

describe('the typed client', () =>
{
  it('asks the API on the page own origin and reads its answer', async () =>
  {
    const server = fakeServer(() => limits);
    const api = createApi(server.send);

    const { data } = await api.GET('/api/limits');

    expect(data?.messageParagraphs).toBe(20);
    expect(server.requests.map((request) => new URL(request.url).pathname)).toEqual(['/api/limits']);
    expect(new URL(server.requests[0]?.url ?? '').origin).toBe(window.location.origin);
  });
});
