import type { Account, Couple, Negotiation } from '../api/client';
import { formatDate, nameOf } from '../texts/format';

/** A member of the couple by id, as the user reads it: "te" for the user. */
export function who(couple: Couple, id: string | null, me: Account | null): string
{
  if (id !== null && id === me?.id)
  {
    return 'te';
  }

  const person = [couple.partnerOne, couple.partnerTwo, couple.therapist].find((member) => member.id === id);

  return person ? nameOf(person) : '—';
}

/** The same, at the start of a sentence: "Tocca a te", "Sam ti ha inviato". */
export function nameIn(couple: Couple, id: string | null): string
{
  const person = [couple.partnerOne, couple.partnerTwo, couple.therapist].find((member) => member.id === id);

  return person ? nameOf(person) : '—';
}

/** "a Sam", "ad Alex": Italian puts a d before a name starting with a. */
export function toWhom(name: string): string
{
  return /^[aA]/.test(name) ? `ad ${name}` : `a ${name}`;
}

export function isOpen(negotiation: Negotiation): boolean
{
  return negotiation.status === 'DRAFT' || negotiation.status === 'ACCEPTED';
}

/**
 * A stipula in one line, after its purpose (texts approved with F6): "richiesta da Alex il 16 novembre 2026 · tocca a Sam",
 * or how it ended.
 */
export function negotiationLine(negotiation: Negotiation, couple: Couple, me: Account | null): string
{
  const requested = `richiesta da ${who(couple, negotiation.request.author, me)} il ${formatDate(negotiation.startedAt)}`;

  if (isOpen(negotiation))
  {
    return `${requested} · tocca ${toWhom(who(couple, negotiation.currentResponder, me))}`;
  }

  const ended = negotiation.status === 'REFUSED' ? 'rifiutata' : 'abbandonata';
  const when = negotiation.closedAt ? ` il ${formatDate(negotiation.closedAt)}` : '';

  return `${requested} · ${ended} da ${who(couple, negotiation.closedBy, me)}${when}`;
}
