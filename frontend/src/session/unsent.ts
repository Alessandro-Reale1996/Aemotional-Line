import { useEffect } from 'react';

/**
 * The forms holding text the user hasn't sent yet (CLAUDE.md, frontend point 7). Nothing is saved in the browser, so
 * while one holds text the app protects it: the browser asks before the page is closed or reloaded, an in-app link asks
 * too, and a lost session shows the login over the page instead of leaving it.
 */
const holders = new Set<symbol>();

export function hasUnsentText(): boolean
{
  return holders.size > 0;
}

/** Forgets every holder: called just before the app leaves the page on purpose, so the browser doesn't ask. */
export function discardUnsentText(): void
{
  holders.clear();
}

/** Declares that the calling form holds unsent text while `holding` is true. */
export function useUnsentText(holding: boolean): void
{
  useEffect(() =>
  {
    if (!holding)
    {
      return undefined;
    }

    const holder = Symbol('unsent text');
    holders.add(holder);

    return () =>
    {
      holders.delete(holder);
    };
  }, [holding]);
}

/** Asks the browser to confirm closing or reloading the page while some text is unsent (where the browser allows it). */
export function guardPageExit(target: Window): () => void
{
  const guard = (event: BeforeUnloadEvent) =>
  {
    if (hasUnsentText())
    {
      event.preventDefault();
    }
  };

  target.addEventListener('beforeunload', guard);

  return () => target.removeEventListener('beforeunload', guard);
}
