import type { Account } from '../api/client';
import { type EndReason, type LoginState, safeReturn } from './navigation';
import type { TabChannel, TabSignal } from './tabs';
import { discardUnsentText, guardPageExit, hasUnsentText } from './unsent';

/** After this long without mouse, keys or touches in any of the app's tabs, the session is ended (frontend point 7). */
export const IDLE_LIMIT = 30 * 60_000;
/** While someone types, at most one request in this time keeps the session alive (frontend point 7). */
export const KEEP_ALIVE_EVERY = 10 * 60_000;
const ACTIVITY_SIGNAL_EVERY = 60_000;
const CHECK_EVERY = 30_000;
const ACTIVITY_EVENTS = ['pointermove', 'pointerdown', 'wheel', 'touchstart', 'scroll'];

export interface SessionDeps
{
  /** Who the browser's session belongs to now, asked to the server; null without one. */
  whoIsLoggedIn(): Promise<Account | null>;
  logOut(): Promise<void>;
  /** A request that keeps the server's session alive. */
  keepAlive(): Promise<unknown>;
  channel: TabChannel;
  /** Leaves the page with a full reload (navigation.ts). */
  reloadAt(path: string, state?: LoginState): void;
  /** The page shown now, to come back to after a login. */
  currentPath(): string;
  now(): number;
}

/**
 * The session as this tab sees it (CLAUDE.md, frontend points 3 and 7, F4 decisions 1-3). It learns the account from the
 * root loader, hears from the API client about every request and about a session that is over, and from the page about
 * the user's activity. When the session is lost it leaves the page for the login, with a full reload; but if a form holds
 * unsent text it "locks" instead: the page is hidden and the login appears over it, and the same account comes back where
 * it was.
 */
export class Session
{
  private account: Account | null = null;
  private locked: EndReason | null = null;
  private ending = false;
  private left = false;
  private lastActivityAt: number;
  private lastRequestAt: number;
  private lastSignalAt = Number.NEGATIVE_INFINITY;
  private readonly listeners = new Set<() => void>();

  constructor(private readonly deps: SessionDeps)
  {
    this.lastActivityAt = deps.now();
    this.lastRequestAt = deps.now();
  }

  setAccount(account: Account | null): void
  {
    this.account = account;
  }

  /** Why the page is locked behind the login, or null. React reads it with useSyncExternalStore. */
  readonly lock = (): EndReason | null => this.locked;

  readonly subscribe = (listener: () => void): (() => void) =>
  {
    this.listeners.add(listener);

    return () => this.listeners.delete(listener);
  };

  /** The API client sent a request: the server's idle time restarts. */
  readonly requestSent = (): void =>
  {
    this.lastRequestAt = this.deps.now();
  };

  /** The API answered 401 AUTHENTICATION_REQUIRED. Without an account it only means "nobody is logged in". */
  readonly sessionLost = (): void =>
  {
    if (this.account && !this.ending)
    {
      this.lose('expired');
    }
  };

  /** Mouse, touch or scrolling: someone is here. The other tabs are told at most once a minute. */
  readonly activity = (): void =>
  {
    const now = this.deps.now();
    this.lastActivityAt = now;

    if (now - this.lastSignalAt >= ACTIVITY_SIGNAL_EVERY)
    {
      this.lastSignalAt = now;
      this.deps.channel.post('activity');
    }
  };

  /** A key pressed: activity, and if nothing reached the server for 10 minutes, one request keeps the session alive. */
  readonly keyPressed = (): void =>
  {
    this.activity();
    const now = this.deps.now();

    if (this.account && !this.locked && !this.ending && now - this.lastRequestAt >= KEEP_ALIVE_EVERY)
    {
      // Counted at once, so that keys pressed before the answer don't send more.
      this.lastRequestAt = now;
      this.deps.keepAlive().catch(() => undefined);
    }
  };

  /** Run every 30 seconds and when the tab comes back: after 30 minutes without activity the session is ended. */
  readonly check = (): void =>
  {
    if (this.account && !this.locked && !this.ending && this.isIdle())
    {
      void this.endForInactivity();
    }
  };

  /** A signal from another tab. */
  readonly signal = (signal: TabSignal): void =>
  {
    if (signal === 'activity')
    {
      this.lastActivityAt = this.deps.now();
    }
    else
    {
      void this.recheck();
    }
  };

  /** The login page logged someone in: the other tabs are told, then the page is left for where the user was going. */
  loggedIn(returnTo?: string): void
  {
    this.deps.channel.post('login');
    this.leaveFor(safeReturn(returnTo) ?? '/');
  }

  /** The login over the page succeeded: the same account goes back to the page; another one starts from the home. */
  loggedInAgain(account: Account): void
  {
    this.deps.channel.post('login');

    if (account.id === this.account?.id)
    {
      this.lastActivityAt = this.deps.now();
      this.lastRequestAt = this.deps.now();
      this.unlock();
    }
    else
    {
      this.leaveFor('/');
    }
  }

  /** "Esci": ends the session, tells the other tabs, and leaves for the login. Throws an ApiError if the server can't be reached. */
  async logOut(): Promise<void>
  {
    this.ending = true;

    try
    {
      await this.deps.logOut();
    }
    catch (failure)
    {
      this.ending = false;
      throw failure;
    }

    this.deps.channel.post('logout');
    this.leaveFor('/accedi', { reason: 'logout' });
  }

  /** The account was deleted, and its session with it: the other tabs are told, the page leaves for the login. */
  accountDeleted(): void
  {
    this.ending = true;
    this.deps.channel.post('logout');
    this.leaveFor('/accedi', { reason: 'deleted' });
  }

  /** Listens to the page and to the other tabs; returns what stops listening. */
  connect(target: Window): () => void
  {
    const options = { capture: true, passive: true };
    const visible = () =>
    {
      if (target.document.visibilityState === 'visible')
      {
        this.check();
      }
    };

    ACTIVITY_EVENTS.forEach((event) => target.addEventListener(event, this.activity, options));
    target.addEventListener('keydown', this.keyPressed, options);
    target.addEventListener('focus', this.check);
    target.addEventListener('pageshow', this.check);
    target.document.addEventListener('visibilitychange', visible);
    const timer = target.setInterval(this.check, CHECK_EVERY);
    const stopListening = this.deps.channel.listen(this.signal);
    const stopGuarding = guardPageExit(target);

    return () =>
    {
      ACTIVITY_EVENTS.forEach((event) => target.removeEventListener(event, this.activity, options));
      target.removeEventListener('keydown', this.keyPressed, options);
      target.removeEventListener('focus', this.check);
      target.removeEventListener('pageshow', this.check);
      target.document.removeEventListener('visibilitychange', visible);
      target.clearInterval(timer);
      stopListening();
      stopGuarding();
    };
  }

  private isIdle(): boolean
  {
    return this.deps.now() - this.lastActivityAt >= IDLE_LIMIT;
  }

  private async endForInactivity(): Promise<void>
  {
    this.ending = true;

    try
    {
      await this.deps.logOut();
    }
    catch
    {
      // The server has most likely ended it already (30 minutes without requests), or can't be reached: the page goes anyway.
    }

    this.ending = false;
    this.deps.channel.post('logout');
    this.lose('inactivity');
  }

  /** Another tab logged in or out: who is logged in now? */
  private async recheck(): Promise<void>
  {
    let current: Account | null;

    try
    {
      current = await this.deps.whoIsLoggedIn();
    }
    catch
    {
      return;
    }

    if (!this.account)
    {
      // A page without a session (the login page, for instance): someone logged in, the page shows it.
      if (current)
      {
        this.leaveFor(this.deps.currentPath());
      }
    }
    else if (current?.id === this.account.id)
    {
      this.unlock();
    }
    else
    {
      this.lose(this.isIdle() ? 'inactivity' : 'elsewhere');
    }
  }

  private lose(reason: EndReason): void
  {
    if (this.locked)
    {
      return;
    }

    if (hasUnsentText())
    {
      this.locked = reason;
      this.emit();
    }
    else
    {
      this.leaveFor('/accedi', { reason, returnTo: this.deps.currentPath() });
    }
  }

  private unlock(): void
  {
    if (this.locked)
    {
      this.locked = null;
      this.emit();
    }
  }

  private leaveFor(path: string, state?: LoginState): void
  {
    if (!this.left)
    {
      this.left = true;
      discardUnsentText();
      this.deps.reloadAt(path, state);
    }
  }

  private emit(): void
  {
    this.listeners.forEach((listener) => listener());
  }
}
