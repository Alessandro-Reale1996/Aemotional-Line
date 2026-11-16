import { useCallback, useEffect, useRef } from 'react';
import type { ShouldRevalidateFunctionArgs } from 'react-router';

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

/**
 * Declares that the calling form holds unsent text while `holding` is true. Returns what releases it at once: a form calls
 * it when its text has been sent, before leaving the page, so that nothing asks for a confirmation.
 */
export function useUnsentText(holding: boolean): () => void
{
  const current = useRef<symbol | null>(null);

  useEffect(() =>
  {
    if (!holding)
    {
      return undefined;
    }

    const holder = Symbol('unsent text');
    holders.add(holder);
    current.current = holder;

    return () =>
    {
      holders.delete(holder);
      current.current = null;
    };
  }, [holding]);

  return useCallback(() =>
  {
    if (current.current)
    {
      holders.delete(current.current);
    }
  }, []);
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

/**
 * A route's `shouldRevalidate` for a page with a form (frontend point 4): while the form holds unsent text the data on
 * screen aren't re-read, so a refusal (409) can't make the form disappear with what was written; the refusal's text says
 * what happened. A change of address is always read.
 */
export function unlessUnsentText({ currentUrl, nextUrl, defaultShouldRevalidate }: ShouldRevalidateFunctionArgs): boolean
{
  return currentUrl.href !== nextUrl.href || !hasUnsentText() ? defaultShouldRevalidate : false;
}
