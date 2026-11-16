import type { Person } from '../api/client';

const DATE = new Intl.DateTimeFormat('it-IT', { day: 'numeric', month: 'long', year: 'numeric' });

/** A day as Italians write it, in the browser's own time zone (CLAUDE.md, frontend point 4): "13 novembre 2026". */
export function formatDate(instant: string): string
{
  return DATE.format(new Date(instant));
}

const TIME = new Intl.DateTimeFormat('it-IT', { hour: '2-digit', minute: '2-digit' });

/** A moment as Italians write it, in the browser's own time zone: "16 novembre 2026, 18:30" (never "2 minutes ago"). */
export function formatDateTime(instant: string | Date): string
{
  const date = typeof instant === 'string' ? new Date(instant) : instant;

  // Composed by hand: Intl's own pattern for a long date with a time varies across browsers ("alle ore").
  return `${DATE.format(date)}, ${TIME.format(date)}`;
}

/** The time of a moment: "18:30". */
export function formatTime(instant: Date): string
{
  return TIME.format(instant);
}

/** A person's name; a patient who deleted their account after the couple ended has none any more (R3 decision 1). */
export function nameOf(person: Person): string
{
  return person.displayName ?? 'Account eliminato';
}
