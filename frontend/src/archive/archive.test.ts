import { describe, expect, it } from 'vitest';

import type { ArchiveEntry } from '../api/client';
import { conversationWith, partner, patient } from '../api/testing';
import { byMessage, dayOf, defaultPeriod, discussionName, highlight, periodInstants, periodProblem, waitingQuestions } from './archive';

function entry(message: number, paragraph: number | null, extra: Partial<ArchiveEntry> = {}): ArchiveEntry
{
  return {
    paragraphId: paragraph === null ? null : `p${paragraph}`,
    type: paragraph === null ? null : 'SIMPLE',
    title: paragraph === null ? null : `Titolo ${paragraph}`,
    subtitle: null,
    body: paragraph === null ? null : 'Testo.',
    discussionId: 0,
    messageId: `m${message}`,
    sender: patient.id,
    sentAt: '2026-11-17T09:00:00Z',
    closedEnvelope: paragraph === null,
    ...extra,
  };
}

describe('the period', () =>
{
  it('is the last seven days by default, today included', () =>
  {
    expect(defaultPeriod(new Date(2026, 10, 3, 9, 30))).toEqual({ dal: '2026-10-28', al: '2026-11-03' });
    expect(dayOf(new Date(2026, 0, 5))).toBe('2026-01-05');
  });

  it('needs two real days, in order', () =>
  {
    expect(periodProblem('2026-11-01', '2026-11-17')).toBeUndefined();
    expect(periodProblem('2026-11-17', '2026-11-17')).toBeUndefined();
    expect(periodProblem('', '2026-11-17')).toBe('Scegli le due date.');
    expect(periodProblem('2026-02-30', '2026-03-01')).toBe('Scegli le due date.');
    expect(periodProblem('17/11/2026', '2026-11-17')).toBe('Scegli le due date.');
    expect(periodProblem('2026-11-18', '2026-11-17')).toBe('La data di inizio viene dopo quella di fine.');
  });

  it('becomes the instants of the user’s local days, the last one included (Europe/Rome in the tests)', () =>
  {
    expect(periodInstants('2026-11-17', '2026-11-17')).toEqual({ from: '2026-11-16T23:00:00.000Z', to: '2026-11-17T23:00:00.000Z' });
    // The night the clocks go back lasts 25 hours.
    expect(periodInstants('2026-10-25', '2026-10-25')).toEqual({ from: '2026-10-24T22:00:00.000Z', to: '2026-10-25T23:00:00.000Z' });
    expect(periodInstants('2026-12-31', '2027-01-01').to).toBe('2027-01-01T23:00:00.000Z');
  });
});

describe('what was found', () =>
{
  const marked = (text: string, searched: string) => highlight(text, searched).map((part) => (part.found ? `[${part.text}]` : part.text)).join('');

  it('is marked ignoring capitals and accents, as the server finds it', () =>
  {
    expect(marked('Perché non mi rispondi? PERCHE!', 'perche')).toBe('[Perché] non mi rispondi? [PERCHE]!');
    expect(marked('Le vacanze al mare', 'VACANZE')).toBe('Le [vacanze] al mare');
    expect(marked('Città', 'citta')).toBe('[Città]');
    // An accent written as a separate mark stays with its letter.
    expect(marked('perché no', 'perché')).toBe('[perché] no');
  });

  it('leaves the text whole when nothing matches', () =>
  {
    expect(highlight('Le vacanze', 'montagna')).toEqual([{ text: 'Le vacanze', found: false }]);
    expect(highlight('Le vacanze', '  ')).toEqual([{ text: 'Le vacanze', found: false }]);
    expect(marked('aaa', 'aa')).toBe('[aa]a');
  });
});

describe('the entries', () =>
{
  it('are grouped by message, in their order', () =>
  {
    const groups = byMessage([entry(1, 1), entry(1, 2), entry(2, null, { sender: partner.id }), entry(3, 3), entry(1, 4)]);

    expect(groups.map((group) => [group.messageId, group.entries.length, group.closedEnvelope])).toEqual([
      ['m1', 2, false],
      ['m2', 1, true],
      ['m3', 1, false],
      ['m1', 1, false],
    ]);
  });

  it('give the questions still waiting: the open ones a reply hasn’t skipped', () =>
  {
    const skipped = entry(1, 1, { type: 'QUESTION' });
    const waiting = entry(2, 2, { type: 'QUESTION' });

    expect(waitingQuestions({ stillOpen: [skipped, waiting], leftUnanswered: [skipped] })).toEqual([waiting]);
  });

  it('name their discussion', () =>
  {
    const conversation = conversationWith([]);
    conversation.discussions.push({ id: 1, title: 'Le vacanze', subtitle: 'Dove', closed: false, originParagraphId: null, lastSender: null, messages: [] });

    expect(discussionName(conversation, 0)).toBe('Discorso principale');
    expect(discussionName(conversation, 1)).toBe('Le vacanze');
  });
});
