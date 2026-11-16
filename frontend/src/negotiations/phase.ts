import type { Account, Negotiation, Purpose } from '../api/client';

/**
 * Where a stipula stands for the person looking at it (CLAUDE.md, F6), from what the API returns:
 * - `answer-request`: the request waits for this partner's answer;
 * - `await-request`: the request waits for the other partner (the requester may withdraw it; the therapist only reads);
 * - `write`: this partner's draft, to review and send with a justification;
 * - `answer-proposal`: a proposal waits for this partner's answer;
 * - `await`: the other partner's turn;
 * - `closed`: refused or abandoned.
 */
export type Phase = 'answer-request' | 'await-request' | 'write' | 'answer-proposal' | 'await' | 'closed';

export function phaseOf(negotiation: Negotiation, me: Account): Phase
{
  if (negotiation.status === 'REFUSED' || negotiation.status === 'ABANDONED')
  {
    return 'closed';
  }

  const myTurn = negotiation.currentResponder === me.id;

  if (negotiation.status === 'DRAFT')
  {
    return myTurn ? 'answer-request' : 'await-request';
  }

  if (!myTurn)
  {
    return 'await';
  }

  if (negotiation.draft)
  {
    return 'write';
  }

  return negotiation.sentProposals.at(-1)?.status === 'WAITING_FOR_RESPONSE' ? 'answer-proposal' : 'await';
}

/** What accepting a proposal does, said before it is done (texts approved with F6). */
export const ACCEPT_TEXTS: Record<Purpose, string> = {
  STARTED_CONVERSATION: 'Accettando, questo testo diventa l’accordo e la conversazione inizia. Non si può annullare.',
  MODIFIED_AGREEMENT: 'Accettando, questo testo sostituisce l’accordo in vigore. Non si può annullare.',
  OPENED_DISCUSSION: 'Accettando, il discorso specifico si apre con questo titolo. Non si può annullare.',
};

/** The notice on the couple's page once the proposal is accepted (F6 decision 3). */
export const ACCEPTED_NOTICES: Record<Purpose, string> = {
  STARTED_CONVERSATION: 'Accordo stipulato: la conversazione è iniziata.',
  MODIFIED_AGREEMENT: 'L’accordo è stato modificato.',
  OPENED_DISCUSSION: 'Il discorso specifico è aperto.',
};

export const ENDED_WITHOUT_AGREEMENT = 'La stipula si è chiusa senza accordo.';
export const REFUSE_REQUEST_TEXT = 'Rifiutando, la stipula si chiude senza accordo. Non si può annullare.';
export const WITHDRAW_TEXT = 'Ritirando la richiesta, la stipula si chiude senza accordo. Non si può annullare.';
export const ABANDON_TEXT = 'Abbandonando, la stipula si chiude senza accordo e resta nell’archivio. Non si può annullare.';
