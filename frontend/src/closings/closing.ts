import type { Account, Closing, ClosingRequest, Couple, Declaration, TherapistRequest } from '../api/client';
import { nameIn, toWhom } from '../negotiations/words';
import { formatDateTime } from '../texts/format';

/*
 * The closing of a specific discussion (CLAUDE.md, gap 4 and F9), from what GET .../closing returns: nobody closes it
 * alone. The rules here mirror the domain's, so that the panel offers only what is possible; the server has the last word.
 */

/** The partners' request still waiting for an answer, if any. */
export function pendingRequest(closing: Closing): ClosingRequest | undefined
{
  const last = closing.requests.at(-1);

  return last?.status === 'PENDING' ? last : undefined;
}

/** The request to the therapist still waiting for a decision, if any. */
export function pendingTherapistRequest(closing: Closing): TherapistRequest | undefined
{
  const last = closing.therapistRequests.at(-1);

  return last?.status === 'PENDING' ? last : undefined;
}

/**
 * Whether this partner may ask the therapist now, as the domain decides: after a failed attempt of theirs (a request the
 * other refused, or one they withdrew) made after their own last request to the therapist. `retry` means they asked
 * before and must try again with the other first; `none` that they haven't failed an attempt yet.
 */
export function therapistOption(closing: Closing, me: string): 'allowed' | 'retry' | 'none'
{
  const mine = closing.therapistRequests.filter((request) => request.requester === me);
  const firstCounting = Math.max(0, ...mine.map((request) => request.previousRequests.length));
  const failed = closing.requests.slice(firstCounting)
    .some((request) => request.requester === me && (request.status === 'REFUSED' || request.status === 'WITHDRAWN'));

  if (failed)
  {
    return 'allowed';
  }

  return mine.length > 0 ? 'retry' : 'none';
}

/** How the discussion was closed, in one sentence (texts approved with F9). */
export function closureLine(closing: Closing, couple: Couple, me: Account): string
{
  const when = closing.closedAt ? ` il ${formatDateTime(closing.closedAt)}` : '';

  if (closing.way !== 'THERAPIST')
  {
    return `Il discorso è stato chiuso${when} di comune accordo.`;
  }

  const by = me.id === couple.therapist.id ? 'da te' : `da ${nameIn(couple, couple.therapist.id)}`;
  const confirmed = closing.therapistRequests.findLast((request) => request.status === 'CONFIRMED');
  const requester = confirmed?.requester;
  const asked = requester === undefined ? '' : requester === me.id ? ', su tua richiesta' : `, su richiesta di ${nameIn(couple, requester)}`;

  return `Il discorso è stato chiuso${when} ${by}${asked}.`;
}

/** The therapist, as a sentence names them: "a Dott. Rossi", "ad Anna". */
export function toTherapist(couple: Couple): string
{
  return toWhom(nameIn(couple, couple.therapist.id));
}

/** One of the earlier requests, in one line: "La tua richiesta · inviata il … · Rifiutata il …". */
export interface HistoryLine
{
  key: string;
  sentAt: string;
  text: string;
  /** A request to the therapist: its justification, for whoever may read it. */
  justification?: string;
}

/** The requests that are over, both kinds, in the order they were sent: the archive of the closing. */
export function history(closing: Closing, couple: Couple, me: Account, labels: { request: Record<ClosingRequest['status'], string>; therapist: Record<TherapistRequest['status'], string> }): HistoryLine[]
{
  const therapist = nameIn(couple, couple.therapist.id);
  const partners = closing.requests
    .filter((request) => request.status !== 'PENDING')
    .map((request, index) => ({
      key: `r${index}`,
      sentAt: request.sentAt,
      text: `${request.requester === me.id ? 'La tua richiesta' : `Richiesta di ${nameIn(couple, request.requester)}`} · inviata il ${formatDateTime(request.sentAt)}`
        + ` · ${labels.request[request.status]}${request.endedAt ? ` il ${formatDateTime(request.endedAt)}` : ''}`,
    }));
  const toTheTherapist = closing.therapistRequests
    .filter((request) => request.status !== 'PENDING')
    .map((request, index) =>
    {
      const asker = request.requester === me.id ? 'La tua richiesta' : `Richiesta di ${nameIn(couple, request.requester)}`;
      const to = me.id === couple.therapist.id ? 'a te' : toWhom(therapist);

      return {
        key: `t${index}`,
        sentAt: request.sentAt,
        text: `${asker} ${to} · inviata il ${formatDateTime(request.sentAt)}`
          + ` · ${labels.therapist[request.status]}${request.decidedAt ? ` il ${formatDateTime(request.decidedAt)}` : ''}`,
        justification: request.justification ?? undefined,
      };
    });

  return [...partners, ...toTheTherapist].sort((one, two) => one.sentAt.localeCompare(two.sentAt));
}

/** One partner's declaration as the viewer may read it. */
export type DeclarationState =
  | { kind: 'missing' }
  | { kind: 'hidden'; declaration: Declaration }
  | { kind: 'shown'; declaration: Declaration };

export function declarationOf(closing: Closing, partner: string): DeclarationState
{
  const declaration = closing.declarations.find((candidate) => candidate.partner === partner);

  if (!declaration)
  {
    return { kind: 'missing' };
  }

  return declaration.outcome === null ? { kind: 'hidden', declaration } : { kind: 'shown', declaration };
}
