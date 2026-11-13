import type { CoupleSummary } from '../api/client';
import { PURPOSE_LABELS } from '../texts/labels';

export const NOTHING_TO_DO = 'Per ora non c’è niente che aspetti una tua risposta.';

function discussion(number: number): string
{
  return number === 0 ? 'Discorso principale' : `Discorso ${number}`;
}

/**
 * What waits for the user in an active couple, in words (CLAUDE.md, frontend point 5; texts approved with F5). For now
 * only text: each step adds a link when its page exists (F5 decision 1).
 */
export function todoItems(summary: CoupleSummary): string[]
{
  return [
    ...summary.negotiationsWaitingForYou.map((waiting) => `${PURPOSE_LABELS[waiting.purpose]}: tocca a te.`),
    ...summary.unopenedMessages.map(({ discussion: number, count }) =>
      `${discussion(number)}: ${count === 1 ? '1 messaggio' : `${count} messaggi`} da aprire.`),
    ...summary.closingRequestsToAnswer.map((number) => `${discussion(number)}: una richiesta di chiusura aspetta la tua risposta.`),
    ...summary.therapistRequestsToDecide.map((number) => `${discussion(number)}: una richiesta di chiusura aspetta la tua decisione.`),
    ...summary.outcomesToDeclare.map((number) => `${discussion(number)}: dichiara l’esito.`),
    ...summary.constraintRequestsToDecide.map(() => 'Una proposta di vincoli aspetta la tua risposta.'),
  ];
}
