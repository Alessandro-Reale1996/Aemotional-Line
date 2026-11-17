import type { Account, Constraint, ConstraintRequest, Couple } from '../api/client';
import { durationMs, formatDuration, placeWords, shortTime } from '../conversation/rules';
import { nameIn } from '../negotiations/words';

/*
 * The constraints in words (CLAUDE.md, F7 decision 5 and F9): sentences composed in Italian from their values, never
 * the domain's English `description`, never the coordinates in a sentence.
 */

const ACTIONS: Record<Constraint['scope'], string> = {
  READ: 'aprire i messaggi',
  WRITE: 'scrivere',
  BOTH: 'aprire i messaggi e scrivere',
};

/** "Puoi scrivere solo dalle 18:00 a mezzanotte.", "Sam deve attendere 2 ore dall’apertura di un messaggio prima di rispondere." */
export function constraintSentence(constraint: Constraint, couple: Couple, me: Account): string
{
  const mine = constraint.user === me.id;
  const name = nameIn(couple, constraint.user);
  const can = mine ? 'Puoi' : `${name} può`;

  switch (constraint.type)
  {
    case 'TIME':
      return `${can} ${ACTIONS[constraint.scope]} solo dalle ${shortTime(constraint.notBefore ?? '')} a mezzanotte.`;
    case 'REPLY_DELAY':
      return `${mine ? 'Devi' : `${name} deve`} attendere ${formatDuration(durationMs(constraint.delay ?? 'PT0S'))} dall’apertura di un messaggio prima di rispondere.`;
    case 'LOCATION':
      return `${can} ${ACTIONS[constraint.scope]} solo ${placeWords(constraint)}.`;
  }
}

/** The constraints ordered by partner, the first partner's first, each partner's in the order given. */
export function byPartner(constraints: Constraint[], couple: Couple): Constraint[]
{
  return [couple.partnerOne.id, couple.partnerTwo.id].flatMap((partner) => constraints.filter((constraint) => constraint.user === partner));
}

/**
 * Where a place is on OpenStreetMap: only a link, opened by a click (frontend point 6), so nothing reaches an outside
 * service while the page is shown.
 */
export function mapLink(latitude: number, longitude: number): string
{
  return `https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=16/${latitude}/${longitude}`;
}

/** Coordinates as the therapist reads them: "45,46420, 9,19000". */
export function coordinates(latitude: number, longitude: number): string
{
  const format = new Intl.NumberFormat('it-IT', { minimumFractionDigits: 5, maximumFractionDigits: 5 });

  return `${format.format(latitude)}, ${format.format(longitude)}`;
}

function otherOf(couple: Couple, partner: string): string
{
  return couple.partnerOne.id === partner ? couple.partnerTwo.id : couple.partnerOne.id;
}

/** Who approved a pending request and who is still missing (texts approved with F9). */
export function approvalLine(request: ConstraintRequest, couple: Couple, me: Account): string
{
  const approved = request.approvals;

  if (approved.length === 0)
  {
    return 'Nessuno l’ha ancora approvata.';
  }

  const by = approved[0] ?? '';
  const missing = otherOf(couple, by);

  if (by === me.id)
  {
    return `Hai approvato: manca l’approvazione di ${nameIn(couple, missing)}.`;
  }

  if (missing === me.id)
  {
    return `Approvata da ${nameIn(couple, by)}: manca la tua approvazione.`;
  }

  return `Approvata da ${nameIn(couple, by)}: manca l’approvazione di ${nameIn(couple, missing)}.`;
}

/** What approving does, said before it is done: at once, or once the other partner approves too. */
export function approveText(request: ConstraintRequest, couple: Couple, me: Account): string
{
  const other = otherOf(couple, me.id);

  return request.approvals.includes(other)
    ? 'Approvando, questi vincoli entrano subito in vigore al posto di quelli attuali.'
    : `Approvando, accetti questi vincoli: entreranno in vigore quando li avrà approvati anche ${nameIn(couple, other)}.`;
}

/** "Proposta di Dott. Rossi", or "La tua proposta" for the therapist who made it. */
export function proposedBy(request: ConstraintRequest, couple: Couple, me: Account): string
{
  return request.proposedBy === me.id ? 'La tua proposta' : `Proposta di ${nameIn(couple, request.proposedBy)}`;
}
