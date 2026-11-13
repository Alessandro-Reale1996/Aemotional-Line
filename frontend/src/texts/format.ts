import type { Person } from '../api/client';

const DATE = new Intl.DateTimeFormat('it-IT', { day: 'numeric', month: 'long', year: 'numeric' });

/** A day as Italians write it, in the browser's own time zone (CLAUDE.md, frontend point 4): "13 novembre 2026". */
export function formatDate(instant: string): string
{
  return DATE.format(new Date(instant));
}

/** A person's name; a patient who deleted their account after the couple ended has none any more (R3 decision 1). */
export function nameOf(person: Person): string
{
  return person.displayName ?? 'Account eliminato';
}
