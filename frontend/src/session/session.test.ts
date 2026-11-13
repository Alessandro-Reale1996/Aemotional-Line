import { renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/errors';
import { patient, therapist } from '../api/testing';
import { IDLE_LIMIT, KEEP_ALIVE_EVERY, Session } from './session';
import { fakeSessionDeps, settle } from './testing';
import { discardUnsentText, hasUnsentText, useUnsentText } from './unsent';

const MINUTE = 60_000;

function loggedIn(whoIsLoggedIn = patient)
{
  const fake = fakeSessionDeps(whoIsLoggedIn);
  const session = new Session(fake.deps);
  session.setAccount(patient);

  return { ...fake, session };
}

function holdUnsentText()
{
  renderHook(() => useUnsentText(true));
}

const connected: (() => void)[] = [];

/** Connects the session to the page, and disconnects it after the test. */
function connect(session: Session)
{
  connected.push(session.connect(window));
}

afterEach(() =>
{
  connected.splice(0).forEach((stop) => stop());
  discardUnsentText();
});

describe('inactivity', () =>
{
  it('ends the session after 30 minutes without activity in any tab, and goes to the login', async () =>
  {
    const { session, deps, posted, advance } = loggedIn();

    advance(IDLE_LIMIT - 1);
    session.check();
    expect(deps.logOut).not.toHaveBeenCalled();

    advance(1);
    session.check();
    await settle();

    expect(deps.logOut).toHaveBeenCalledOnce();
    expect(posted).toContain('logout');
    expect(deps.reloadAt).toHaveBeenCalledWith('/accedi', { reason: 'inactivity', returnTo: '/conversazioni/42' });
  });

  it('counts the activity of the other tabs', async () =>
  {
    const { session, deps, advance, fromAnotherTab } = loggedIn();
    connect(session);

    advance(20 * MINUTE);
    fromAnotherTab('activity');
    advance(20 * MINUTE);
    session.check();
    await settle();

    expect(deps.logOut).not.toHaveBeenCalled();
  });

  it('tells the other tabs of this tab activity at most once a minute', () =>
  {
    const { session, posted, advance } = loggedIn();

    session.activity();
    advance(30_000);
    session.activity();
    advance(30_000);
    session.activity();

    expect(posted).toEqual(['activity', 'activity']);
  });

  it('listens to the page: a movement is activity, and the check runs on its own', async () =>
  {
    vi.useFakeTimers();

    try
    {
      const { session, deps, advance } = loggedIn();
      const stop = session.connect(window);

      advance(IDLE_LIMIT - MINUTE);
      window.dispatchEvent(new Event('pointermove'));
      advance(IDLE_LIMIT - MINUTE);
      vi.advanceTimersByTime(30_000);
      expect(deps.logOut).not.toHaveBeenCalled();

      advance(2 * MINUTE);
      vi.advanceTimersByTime(30_000);
      expect(deps.logOut).toHaveBeenCalledOnce();
      stop();
    }
    finally
    {
      vi.useRealTimers();
    }
  });

  it('locks a page holding unsent text instead of leaving it', async () =>
  {
    const { session, deps, advance } = loggedIn();
    holdUnsentText();

    advance(IDLE_LIMIT);
    session.check();
    await settle();

    expect(deps.logOut).toHaveBeenCalledOnce();
    expect(deps.reloadAt).not.toHaveBeenCalled();
    expect(session.lock()).toBe('inactivity');
  });

  it('never ends a page without a session', async () =>
  {
    const fake = fakeSessionDeps();
    const session = new Session(fake.deps);

    fake.advance(2 * IDLE_LIMIT);
    session.check();
    await settle();

    expect(fake.deps.logOut).not.toHaveBeenCalled();
  });
});

describe('keeping the session alive while someone writes', () =>
{
  it('sends one request when a key is pressed 10 minutes after the last one', () =>
  {
    const { session, deps, advance } = loggedIn();

    advance(KEEP_ALIVE_EVERY - 1);
    session.keyPressed();
    expect(deps.keepAlive).not.toHaveBeenCalled();

    advance(1);
    session.keyPressed();
    session.keyPressed();
    expect(deps.keepAlive).toHaveBeenCalledOnce();
  });

  it('counts the requests the app sends anyway', () =>
  {
    const { session, deps, advance } = loggedIn();

    advance(8 * MINUTE);
    session.requestSent();
    advance(8 * MINUTE);
    session.keyPressed();

    expect(deps.keepAlive).not.toHaveBeenCalled();
  });

  it('sends nothing while reading, or without a session', () =>
  {
    const { session, deps, advance } = loggedIn();
    const anonymous = fakeSessionDeps();
    const nobody = new Session(anonymous.deps);

    advance(2 * KEEP_ALIVE_EVERY);
    anonymous.advance(2 * KEEP_ALIVE_EVERY);
    session.activity();
    nobody.keyPressed();

    expect(deps.keepAlive).not.toHaveBeenCalled();
    expect(anonymous.deps.keepAlive).not.toHaveBeenCalled();
  });
});

describe('a session lost', () =>
{
  it('leaves the page for the login, which comes back to it', () =>
  {
    const { session, deps } = loggedIn();

    session.sessionLost();

    expect(deps.reloadAt).toHaveBeenCalledWith('/accedi', { reason: 'expired', returnTo: '/conversazioni/42' });
  });

  it('means nothing on a page without a session', () =>
  {
    const fake = fakeSessionDeps();
    new Session(fake.deps).sessionLost();

    expect(fake.deps.reloadAt).not.toHaveBeenCalled();
  });

  it('locks a page holding unsent text; the same account unlocks it, with the text', () =>
  {
    const { session, deps, posted } = loggedIn();
    holdUnsentText();

    session.sessionLost();
    expect(session.lock()).toBe('expired');
    expect(deps.reloadAt).not.toHaveBeenCalled();

    session.loggedInAgain(patient);
    expect(session.lock()).toBeNull();
    expect(hasUnsentText()).toBe(true);
    expect(posted).toContain('login');
  });

  it('starts from the home when another account logs in over the page, and the text goes', () =>
  {
    const { session, deps } = loggedIn();
    holdUnsentText();

    session.sessionLost();
    session.loggedInAgain(therapist);

    expect(deps.reloadAt).toHaveBeenCalledWith('/', undefined);
    expect(hasUnsentText()).toBe(false);
  });
});

describe('the other tabs', () =>
{
  it('make a tab ask who is logged in: nobody, and it goes to the login', async () =>
  {
    const { session, deps, fromAnotherTab } = loggedIn();
    deps.whoIsLoggedIn.mockResolvedValue(null);
    connect(session);

    fromAnotherTab('logout');
    await settle();

    expect(deps.reloadAt).toHaveBeenCalledWith('/accedi', { reason: 'elsewhere', returnTo: '/conversazioni/42' });
  });

  it('leave a tab as it is when the same account is still logged in', async () =>
  {
    const { session, deps, fromAnotherTab } = loggedIn(patient);
    connect(session);

    fromAnotherTab('login');
    await settle();

    expect(deps.reloadAt).not.toHaveBeenCalled();
  });

  it('make a tab leave when another account logged in, or lock it if it holds text', async () =>
  {
    const leaving = loggedIn(therapist);
    connect(leaving.session);
    leaving.fromAnotherTab('login');
    await settle();
    expect(leaving.deps.reloadAt).toHaveBeenCalledWith('/accedi', { reason: 'elsewhere', returnTo: '/conversazioni/42' });

    const holding = loggedIn(therapist);
    holdUnsentText();
    connect(holding.session);
    holding.fromAnotherTab('login');
    await settle();
    expect(holding.deps.reloadAt).not.toHaveBeenCalled();
    expect(holding.session.lock()).toBe('elsewhere');
  });

  it('unlock a locked tab when the same account logs in again elsewhere', async () =>
  {
    const { session, deps, fromAnotherTab } = loggedIn(patient);
    holdUnsentText();
    connect(session);
    session.sessionLost();

    fromAnotherTab('login');
    await settle();

    expect(session.lock()).toBeNull();
    expect(deps.reloadAt).not.toHaveBeenCalled();
  });

  it('reload a page without a session when someone logs in', async () =>
  {
    const fake = fakeSessionDeps(patient);
    const session = new Session(fake.deps);
    connect(session);

    fake.fromAnotherTab('login');
    await settle();
    expect(fake.deps.reloadAt).toHaveBeenCalledOnce();
    expect(fake.deps.reloadAt).toHaveBeenCalledWith('/conversazioni/42', undefined);
  });
});

describe('logging in and out', () =>
{
  it('goes back where the user was after the login, and only to a page of the app', () =>
  {
    const first = fakeSessionDeps();
    new Session(first.deps).loggedIn('/conversazioni/42');
    expect(first.deps.reloadAt).toHaveBeenCalledWith('/conversazioni/42', undefined);
    expect(first.posted).toEqual(['login']);

    for (const unsafe of ['https://example.org/', '//example.org', '/accedi', undefined])
    {
      const other = fakeSessionDeps();
      new Session(other.deps).loggedIn(unsafe);
      expect(other.deps.reloadAt).toHaveBeenCalledWith('/', undefined);
    }
  });

  it('ends the session, tells the other tabs and goes to the login', async () =>
  {
    const { session, deps, posted } = loggedIn();
    holdUnsentText();

    await session.logOut();

    expect(deps.logOut).toHaveBeenCalledOnce();
    expect(posted).toEqual(['logout']);
    expect(deps.reloadAt).toHaveBeenCalledWith('/accedi', { reason: 'logout' });
    expect(hasUnsentText()).toBe(false);
  });

  it('stays on the page when the server can not be reached', async () =>
  {
    const { session, deps, posted } = loggedIn();
    deps.logOut.mockRejectedValue(ApiError.unreachable());

    await expect(session.logOut()).rejects.toMatchObject({ unreachable: true });

    expect(posted).toEqual([]);
    expect(deps.reloadAt).not.toHaveBeenCalled();
  });
});
