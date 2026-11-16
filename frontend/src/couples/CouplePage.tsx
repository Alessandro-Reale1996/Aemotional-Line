import { useState } from 'react';
import Button from 'react-bootstrap/Button';
import { Link, useLoaderData, useNavigate } from 'react-router';

import { type Account, ask, type Couple, type Negotiation } from '../api/client';
import { useAccount, useApp } from '../app/context';
import { ButtonLink, Notice } from '../components/frames';
import { negotiationLine } from '../negotiations/words';
import { PasswordConfirmDialog } from '../components/PasswordConfirmDialog';
import { nameOf } from '../texts/format';
import { PURPOSE_LABELS } from '../texts/labels';
import { coupleState, partnersOf } from './CoupleCard';

export const END_TEXT =
  'Concludendo il percorso la conversazione diventa di sola lettura: nessuno potrà più scrivere, proporre o chiudere nulla, e le richieste in sospeso restano come sono. Non si può annullare.';
export const DELETE_DATA_TEXT =
  'Verranno eliminati per sempre la conversazione, le stipule e l’invito di questa coppia; gli account delle due persone restano. Non si può annullare.';
export const DATA_DELETED = 'I dati della coppia sono stati eliminati.';

export interface CoupleData
{
  couple: Couple;
  /** The stipule in progress. */
  negotiations: Negotiation[];
  /** The failed attempts to start the conversation, while it doesn't exist (later failures are in the archive). */
  failedStarts: Negotiation[];
}

const AGREEMENT: readonly string[] = ['STARTED_CONVERSATION', 'MODIFIED_AGREEMENT'];

/**
 * /coppie/:id (CLAUDE.md, frontend point 5, F5 and F6): the people and the state of a couple, from the user's own couples;
 * the stipule in progress, the buttons that start one, the failed attempts to start the conversation. The therapist ends
 * the couple, then may delete its data, each confirmed with the password.
 */
export function CouplePage()
{
  const { couple, negotiations, failedStarts } = useLoaderData() as CoupleData;
  const me = useAccount();
  const { api } = useApp();
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState<'end' | 'delete'>();
  const path = { id: couple.id };
  const isTherapist = me?.id === couple.therapist.id;

  return (
    <>
      <Notice />
      <h1 className="h4 mb-3">{partnersOf(couple)}</h1>
      <p className="mb-1">Terapeuta: {nameOf(couple.therapist)}</p>
      <p>{coupleState(couple)}</p>
      <section className="mb-4">
        <h2 className="h5">Stipule in corso</h2>
        <NegotiationList negotiations={negotiations} couple={couple} me={me} empty="Nessuna stipula in corso." />
        {me && couple.active && me.id !== couple.therapist.id && (
          <div className="d-flex flex-wrap gap-2">
            {!negotiations.some((negotiation) => AGREEMENT.includes(negotiation.purpose)) && (
              <ButtonLink to={`/coppie/${couple.id}/nuovo-accordo`} variant="outline-primary">
                {couple.conversation ? 'Proponi una modifica dell’accordo' : 'Proponi l’accordo'}
              </ButtonLink>
            )}
            {couple.conversation && (
              <ButtonLink to={`/conversazioni/${couple.conversation}/nuovo-discorso`} variant="outline-primary">
                Proponi un discorso specifico
              </ButtonLink>
            )}
          </div>
        )}
      </section>
      {!couple.conversation && failedStarts.length > 0 && (
        <section className="mb-4">
          <h2 className="h5">Tentativi non riusciti</h2>
          <NegotiationList negotiations={failedStarts} couple={couple} me={me} />
        </section>
      )}
      {isTherapist && couple.active && (
        <Button variant="outline-danger" onClick={() => setConfirming('end')}>
          Concludi il percorso
        </Button>
      )}
      {isTherapist && !couple.active && (
        <Button variant="outline-danger" onClick={() => setConfirming('delete')}>
          Elimina i dati
        </Button>
      )}
      <PasswordConfirmDialog
        show={confirming === 'end'}
        title="Concludere il percorso?"
        text={END_TEXT}
        confirm="Concludi il percorso"
        action={(password) => ask(api.POST('/api/couples/{id}/end', { params: { path }, body: { password } }))}
        onDone={() => setConfirming(undefined)}
        onCancel={() => setConfirming(undefined)}
      />
      <PasswordConfirmDialog
        show={confirming === 'delete'}
        title="Eliminare i dati?"
        text={DELETE_DATA_TEXT}
        confirm="Elimina i dati"
        reread={false}
        action={(password) => ask(api.POST('/api/couples/{id}/data-deletion', { params: { path }, body: { password } }))}
        onDone={() => void navigate('/', { replace: true, state: { notice: DATA_DELETED } })}
        onCancel={() => setConfirming(undefined)}
      />
    </>
  );
}

function NegotiationList({ negotiations, couple, me, empty }: { negotiations: Negotiation[]; couple: Couple; me: Account | null; empty?: string })
{
  if (negotiations.length === 0)
  {
    return empty ? <p className="text-body-secondary">{empty}</p> : null;
  }

  return (
    <ul>
      {negotiations.map((negotiation) => (
        <li key={negotiation.id}>
          <Link to={`/stipule/${negotiation.id}`}>{PURPOSE_LABELS[negotiation.purpose]}</Link> · {negotiationLine(negotiation, couple, me)}
        </li>
      ))}
    </ul>
  );
}
