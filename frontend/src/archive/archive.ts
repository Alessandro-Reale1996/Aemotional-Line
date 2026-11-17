import type { ArchiveEntry, ArchiveSearchResult, Conversation, UnansweredQuestions } from '../api/client';
import { MAIN_DISCUSSION } from '../conversation/Letters';

/*
 * The archive's rules that the interface applies itself (CLAUDE.md, F10), kept apart from the components so that they can
 * be tested alone.
 */

// ---- The period -----------------------------------------------------------------------------------------------------

const DAY = /^(\d{4})-(\d{2})-(\d{2})$/;

/** A day as the date fields and the address write it: "2026-11-17", in the browser's own time zone. */
export function dayOf(date: Date): string
{
  return `${String(date.getFullYear()).padStart(4, '0')}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

/** The local midnight starting a day, or undefined when the text isn't a real day. */
function midnight(day: string, plusDays = 0): Date | undefined
{
  const parts = DAY.exec(day);

  if (!parts)
  {
    return undefined;
  }

  const [year, month, date] = [Number(parts[1]), Number(parts[2]), Number(parts[3])];
  const start = new Date(year, month - 1, date);
  start.setFullYear(year);

  if (start.getMonth() !== month - 1 || start.getDate() !== date)
  {
    return undefined;
  }

  start.setDate(start.getDate() + plusDays);

  return start;
}

/** The default period (F10 decision 6): the last seven days, today included. */
export function defaultPeriod(now: Date): { dal: string; al: string }
{
  const first = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6);

  return { dal: dayOf(first), al: dayOf(now) };
}

/** What is wrong with a period, in words (texts approved with F10); undefined when it can be shown. */
export function periodProblem(dal: string, al: string): string | undefined
{
  const from = midnight(dal);
  const to = midnight(al);

  if (!from || !to)
  {
    return 'Scegli le due date.';
  }

  return from.getTime() > to.getTime() ? 'La data di inizio viene dopo quella di fine.' : undefined;
}

/**
 * The instants the API wants for the user's local days, `al` included: from the midnight starting `dal` to the midnight
 * ending `al` (the API excludes `to`).
 */
export function periodInstants(dal: string, al: string): { from: string; to: string }
{
  return { from: (midnight(dal) as Date).toISOString(), to: (midnight(al, 1) as Date).toISOString() };
}

// ---- Highlighting what was found ------------------------------------------------------------------------------------

/** A character as the database compares it: no accents, no capitals (the schema's collation, R8). */
function folded(text: string): { text: string; start: number[]; end: number[] }
{
  let result = '';
  const start: number[] = [];
  const end: number[] = [];

  for (let index = 0; index < text.length;)
  {
    const character = String.fromCodePoint(text.codePointAt(index) ?? 0);
    const plain = character.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();

    for (let unit = 0; unit < plain.length; unit++)
    {
      start.push(index);
      end.push(index + character.length);
    }

    result += plain;
    index += character.length;
  }

  return { text: result, start, end };
}

export interface Part
{
  text: string;
  found: boolean;
}

/** A text cut where the searched words appear, ignoring accents and capitals as the server does (F10 decision 3). */
export function highlight(text: string, searched: string): Part[]
{
  const haystack = folded(text);
  const needle = folded(searched).text;
  const parts: Part[] = [];
  let from = 0;

  if (needle.trim() === '')
  {
    return [{ text, found: false }];
  }

  for (let at = haystack.text.indexOf(needle); at >= 0; at = haystack.text.indexOf(needle, at + needle.length))
  {
    const begins = haystack.start[at] ?? 0;
    let ends = haystack.end[at + needle.length - 1] ?? text.length;

    // An accent written as a separate mark belongs to the letter before it.
    while (ends < text.length && /\p{M}/u.test(text.charAt(ends)))
    {
      ends++;
    }

    if (begins > from)
    {
      parts.push({ text: text.slice(from, begins), found: false });
    }

    parts.push({ text: text.slice(begins, ends), found: true });
    from = ends;
  }

  if (from < text.length)
  {
    parts.push({ text: text.slice(from), found: false });
  }

  return parts;
}

// ---- Entries --------------------------------------------------------------------------------------------------------

/** The entries of one message, in a row. */
export interface MessageGroup
{
  messageId: string;
  discussionId: number;
  sender: string;
  sentAt: string;
  closedEnvelope: boolean;
  entries: ArchiveEntry[];
}

/** Consecutive entries of the same message, together: one card per message (F10). */
export function byMessage(entries: ArchiveEntry[]): MessageGroup[]
{
  const groups: MessageGroup[] = [];

  for (const entry of entries)
  {
    const last = groups.at(-1);

    if (last?.messageId === entry.messageId)
    {
      last.entries.push(entry);
    }
    else
    {
      groups.push({
        messageId: entry.messageId,
        discussionId: entry.discussionId,
        sender: entry.sender,
        sentAt: entry.sentAt,
        closedEnvelope: entry.closedEnvelope,
        entries: [entry],
      });
    }
  }

  return groups;
}

/** The questions still waiting for a reply: the open ones a reply hasn't skipped for good. */
export function waitingQuestions(view: UnansweredQuestions): ArchiveEntry[]
{
  const skipped = new Set(view.leftUnanswered.map((entry) => entry.paragraphId));

  return view.stillOpen.filter((entry) => !skipped.has(entry.paragraphId));
}

/** A discussion's name: its title, or "Discorso principale". */
export function discussionName(conversation: Conversation, number: number): string
{
  return conversation.discussions.find((discussion) => discussion.id === number)?.title ?? MAIN_DISCUSSION;
}

// ---- The last search, in memory only (F10 decision 2) ----------------------------------------------------------------

let lastSearch: { conversation: string; text: string; result: ArchiveSearchResult } | undefined;

/**
 * Keeps the last search of the tab, so that coming back from a topic history finds it again. In memory only: never in the
 * address, the history's state (which the browser may save to disk) or any storage; a reload or a logout forgets it.
 */
export function rememberSearch(conversation: string, text: string, result: ArchiveSearchResult): void
{
  lastSearch = { conversation, text, result };
}

export function rememberedSearch(conversation: string): { text: string; result: ArchiveSearchResult } | undefined
{
  return lastSearch?.conversation === conversation ? { text: lastSearch.text, result: lastSearch.result } : undefined;
}

/** For the tests: forgets the last search. */
export function forgetSearch(): void
{
  lastSearch = undefined;
}
