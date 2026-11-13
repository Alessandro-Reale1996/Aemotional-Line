import { useState } from 'react';
import Button from 'react-bootstrap/Button';
import { useLoaderData, useNavigate } from 'react-router';

import { ask, type Couple } from '../api/client';
import { useAccount, useApp } from '../app/context';
import { Notice } from '../components/frames';
import { PasswordConfirmDialog } from '../components/PasswordConfirmDialog';
import { nameOf } from '../texts/format';
import { coupleState, partnersOf } from './CoupleCard';

export const END_TEXT =
  'Concludendo il percorso la conversazione diventa di sola lettura: nessuno potrà più scrivere, proporre o chiudere nulla, e le richieste in sospeso restano come sono. Non si può annullare.';
export const DELETE_DATA_TEXT =
  'Verranno eliminati per sempre la conversazione, le stipule e l’invito di questa coppia; gli account delle due persone restano. Non si può annullare.';
export const DATA_DELETED = 'I dati della coppia sono stati eliminati.';

/**
 * /coppie/:id (CLAUDE.md, frontend point 5): the people and the state of a couple, from the user's own couples. The
 * therapist ends the couple, then may delete its data, each confirmed with the password. F6 adds the negotiations.
 */
export function CouplePage()
{
  const couple = useLoaderData() as Couple;
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
