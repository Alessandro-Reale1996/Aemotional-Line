/** Why the login page is shown, and where to go back afterwards (CLAUDE.md, frontend point 3). */
export type EndReason = 'expired' | 'inactivity' | 'elsewhere' | 'logout' | 'deleted';

export interface LoginState
{
  reason?: EndReason;
  returnTo?: string;
}

/**
 * Leaves the page with a full reload at `path`, so that nothing the page held stays in memory (frontend point 3). The
 * state goes into the history entry, where React Router reads it as the location's state: it survives the reload and never
 * appears in the address. The entry is replaced, so the back button doesn't come back to a page that is gone.
 */
export function reloadAt(path: string, state?: LoginState): void
{
  const current = window.history.state as { idx?: unknown } | null;
  const idx = typeof current?.idx === 'number' ? current.idx : 0;

  // React Router's own shape for a history entry: usr is the location's state.
  window.history.replaceState({ usr: state ?? null, key: Math.random().toString(36).slice(2, 10), idx }, '', path);
  window.location.reload();
}

/** A page to go back to after the login: only a path of this app, never the login page itself. */
export function safeReturn(path: unknown): string | undefined
{
  if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//') || path.startsWith('/accedi'))
  {
    return undefined;
  }

  return path;
}
