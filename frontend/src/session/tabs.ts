/**
 * The signal between the app's tabs (CLAUDE.md, frontend point 7 and F4 decisions 1-2). The cookies are shared by the
 * whole browser, so a login or a logout in one tab changes every tab's session. The signal carries nothing but its kind:
 * a tab receiving "login" or "logout" asks the server who is logged in now; "activity" tells that someone is using the app.
 */
export type TabSignal = 'activity' | 'login' | 'logout';

export interface TabChannel
{
  post(signal: TabSignal): void;
  listen(receive: (signal: TabSignal) => void): () => void;
}

const SIGNALS: readonly unknown[] = ['activity', 'login', 'logout'];

/** The browser's BroadcastChannel: it reaches the other tabs of the same origin, never the tab that posts. */
export function browserTabChannel(): TabChannel
{
  const channel = typeof BroadcastChannel === 'function' ? new BroadcastChannel('aemotional-line') : undefined;

  return {
    post: (signal) => channel?.postMessage(signal),
    listen: (receive) =>
    {
      const handle = (event: MessageEvent) =>
      {
        if (SIGNALS.includes(event.data))
        {
          receive(event.data as TabSignal);
        }
      };

      channel?.addEventListener('message', handle);

      return () => channel?.removeEventListener('message', handle);
    },
  };
}
