import type { CoupleSummary } from '../api/client';
import { PURPOSE_LABELS } from '../texts/labels';

export const NOTHING_TO_DO = 'Per ora non c’è niente che aspetti una tua risposta.';

function discussion(number: number): string
{
  return number === 0 ? 'Discorso principale' : `Discorso ${number}`;
}

/** One thing waiting for the user; `to` is its page, once the page exists. */
export interface TodoItem
{
  text: string;
  to?: string;
}

/**
 * What waits for the user in an active couple, in words (CLAUDE.md, frontend point 5; texts approved with F5). Each step
 * adds the link of the items whose page it brings (F5 decision 1): the stipule since F6, the discussions since F7.
 */
export function todoItems(summary: CoupleSummary): TodoItem[]
{
  const words = (text: string): TodoItem => ({ text });
  // A discussion's page, once the conversation exists (F7).
  const at = (number: number) => (text: string): TodoItem =>
    (summary.conversation ? { text, to: `/conversazioni/${summary.conversation}/discorsi/${number}` } : words(text));

  return [
    ...summary.negotiationsWaitingForYou.map((waiting) => ({ text: `${PURPOSE_LABELS[waiting.purpose]}: tocca a te.`, to: `/stipule/${waiting.id}` })),
    ...summary.unopenedMessages.map(({ discussion: number, count }) =>
      at(number)(`${discussion(number)}: ${count === 1 ? '1 messaggio' : `${count} messaggi`} da aprire.`)),
    ...summary.closingRequestsToAnswer.map((number) => at(number)(`${discussion(number)}: una richiesta di chiusura aspetta la tua risposta.`)),
    ...summary.therapistRequestsToDecide.map((number) => at(number)(`${discussion(number)}: una richiesta di chiusura aspetta la tua decisione.`)),
    ...summary.outcomesToDeclare.map((number) => at(number)(`${discussion(number)}: dichiara l’esito.`)),
    ...summary.constraintRequestsToDecide.map(() => words('Una proposta di vincoli aspetta la tua risposta.')),
  ];
}
