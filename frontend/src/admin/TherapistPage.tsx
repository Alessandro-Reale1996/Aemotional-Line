import { useState } from 'react';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import { Link, useLoaderData } from 'react-router';

import { ask, type EndedCouple, type Therapist } from '../api/client';
import { useApp } from '../app/context';
import { PasswordConfirmDialog } from '../components/PasswordConfirmDialog';
import { DELETE_DATA_TEXT } from '../couples/CouplePage';
import { formatDate } from '../texts/format';

export interface TherapistData
{
  therapist: Therapist;
  ended: EndedCouple[];
}

/**
 * /amministrazione/terapeuti/:id (CLAUDE.md, R4 decision and F5): a disabled therapist's ended couples, by end date only,
 * whose data the administrator may delete without reading them.
 */
export function TherapistPage()
{
  const { therapist, ended } = useLoaderData() as TherapistData;

  return (
    <>
      <p className="mb-2">
        <Link to="/amministrazione">Torna ai terapeuti</Link>
      </p>
      <h1 className="h4 mb-4">Percorsi conclusi di «{therapist.displayName}»</h1>
      {ended.length === 0 && <p className="text-body-secondary">Nessun percorso concluso.</p>}
      {ended.map((couple) => (
        <EndedCoupleCard key={couple.id} couple={couple} />
      ))}
    </>
  );
}

function EndedCoupleCard({ couple }: { couple: EndedCouple })
{
  const { api } = useApp();
  const [confirming, setConfirming] = useState(false);

  return (
    <Card body className="mb-2">
      <div className="mb-2">Percorso concluso il {formatDate(couple.endedAt)}</div>
      <Button variant="outline-danger" size="sm" onClick={() => setConfirming(true)}>
        Elimina i dati
      </Button>
      <PasswordConfirmDialog
        show={confirming}
        title="Eliminare i dati?"
        text={DELETE_DATA_TEXT}
        confirm="Elimina i dati"
        action={(password) => ask(api.POST('/api/couples/{id}/data-deletion', { params: { path: { id: couple.id } }, body: { password } }))}
        onDone={() => setConfirming(false)}
        onCancel={() => setConfirming(false)}
      />
    </Card>
  );
}
