import Card from 'react-bootstrap/Card';
import { Link } from 'react-router';

import type { Couple, Person } from '../api/client';
import { formatDate, nameOf } from '../texts/format';

/** "Percorso in corso", or the day it ended. */
export function coupleState(couple: Couple): string
{
  return couple.active || !couple.endedAt ? 'Percorso in corso' : `Percorso concluso il ${formatDate(couple.endedAt)}`;
}

/** The two partners of a couple or of an invitation: "Alex e Sam". */
export function partnersOf(pair: { partnerOne: Person; partnerTwo: Person }): string
{
  return `${nameOf(pair.partnerOne)} e ${nameOf(pair.partnerTwo)}`;
}

/** A couple in a list: the two partners (a link to the couple's page), the therapist, the state, what waits. */
export function CoupleCard({ couple, todo = [] }: { couple: Couple; todo?: string[] })
{
  return (
    <Card body className="mb-2">
      <Link to={`/coppie/${couple.id}`} className="fw-semibold">
        {partnersOf(couple)}
      </Link>
      <div className="text-body-secondary small">
        Terapeuta: {nameOf(couple.therapist)} · {coupleState(couple)}
      </div>
      {todo.length > 0 && (
        <ul className="mt-2 mb-0">
          {todo.map((item, index) => (
            <li key={index}>{item}</li>
          ))}
        </ul>
      )}
    </Card>
  );
}
