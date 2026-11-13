import { useState } from 'react';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import { useLoaderData } from 'react-router';

import { type Account, ask, type Couple, type Invitation, type Summary } from '../api/client';
import { useAccount, useApp } from '../app/context';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ErrorText } from '../components/ErrorText';
import { ButtonLink, Notice } from '../components/frames';
import { useCommand } from '../components/useCommand';
import { CoupleCard, partnersOf } from '../couples/CoupleCard';
import { formatDate, nameOf } from '../texts/format';
import { NOTHING_TO_DO, todoItems } from './todo';

export interface HomeData
{
  summary: Summary;
  couples: Couple[];
}

export const NO_COUPLE =
  'Non fai ancora parte di un percorso di coppia. Quando chi ti segue in terapia ti invierà un invito, riceverai un’email e lo troverai qui.';

/** The home by role (CLAUDE.md, frontend point 5): the administrator's is /amministrazione. */
export function HomePage()
{
  const account = useAccount();
  const data = useLoaderData() as HomeData;

  if (!account)
  {
    return null;
  }

  return account.role === 'THERAPIST' ? <TherapistHome me={account} data={data} /> : <PatientHome me={account} data={data} />;
}

/** A patient's home: invitations, what waits for them, the couple in progress, the ended ones at the bottom. */
function PatientHome({ me, data }: { me: Account; data: HomeData })
{
  const active = data.couples.find((couple) => couple.active);
  const ended = data.couples.filter((couple) => !couple.active);
  const summary = data.summary.couples.find((candidate) => candidate.couple === active?.id);
  const invitations = data.summary.pendingInvitations;
  const todo = summary ? todoItems(summary) : [];

  return (
    <>
      <Notice />
      <h1 className="h4 mb-4">Ciao, {me.displayName}</h1>
      {invitations.length > 0 && (
        <section className="mb-4">
          <h2 className="h5">Inviti</h2>
          {invitations.map((invitation) => (
            <ReceivedInvitation key={invitation.id} invitation={invitation} me={me} />
          ))}
        </section>
      )}
      {active ? (
        <>
          <section className="mb-4">
            <h2 className="h5">Da fare</h2>
            {todo.length > 0 ? (
              <ul>
                {todo.map((item, index) => (
                  <li key={index}>{item}</li>
                ))}
              </ul>
            ) : (
              <p className="text-body-secondary">{NOTHING_TO_DO}</p>
            )}
          </section>
          <section className="mb-4">
            <h2 className="h5">Percorso in corso</h2>
            <CoupleCard couple={active} />
          </section>
        </>
      ) : (
        invitations.length === 0 && <p>{NO_COUPLE}</p>
      )}
      {ended.length > 0 && (
        <section>
          <h2 className="h5">Percorsi conclusi</h2>
          {ended.map((couple) => (
            <CoupleCard key={couple.id} couple={couple} />
          ))}
        </section>
      )}
    </>
  );
}

function otherPartner(invitation: Invitation, me: Account)
{
  return invitation.partnerOne.id === me.id ? invitation.partnerTwo : invitation.partnerOne;
}

/** An invitation a patient received: accept, or decline after a confirmation. */
function ReceivedInvitation({ invitation, me }: { invitation: Invitation; me: Account })
{
  const { api } = useApp();
  const [confirming, setConfirming] = useState(false);
  const path = { params: { path: { id: invitation.id } } };
  const accept = useCommand(() => ask(api.POST('/api/invitations/{id}/accept', path)));
  const decline = useCommand(() => ask(api.POST('/api/invitations/{id}/decline', path)));
  const other = nameOf(otherPartner(invitation, me));
  const pending = accept.pending || decline.pending;

  return (
    <Card body className="mb-2">
      <p className="mb-2">
        {nameOf(invitation.therapist)} ti invita a iniziare un percorso di coppia con {other}. Scade il {formatDate(invitation.expiresAt)}.
      </p>
      {invitation.acceptedBy.includes(me.id) ? (
        <p className="mb-0 text-body-secondary">Hai accettato: manca la risposta di {other}.</p>
      ) : (
        <div className="d-flex flex-wrap gap-2">
          <Button onClick={() => void accept.run()} disabled={pending}>
            Accetta
          </Button>
          <Button variant="outline-secondary" onClick={() => setConfirming(true)} disabled={pending}>
            Rifiuta
          </Button>
        </div>
      )}
      <ErrorText error={accept.error ?? decline.error} />
      <ConfirmDialog
        show={confirming}
        title="Rifiutare l’invito?"
        text={`Se rifiuti, l’invito si chiude anche per ${other}.`}
        confirm="Rifiuta"
        cancel="Annulla"
        onConfirm={() =>
        {
          setConfirming(false);
          void decline.run();
        }}
        onCancel={() => setConfirming(false)}
      />
    </Card>
  );
}

/** A therapist's home: the couples in progress with what waits, the invitations sent, the ended couples. */
function TherapistHome({ data }: { me: Account; data: HomeData })
{
  const active = data.couples.filter((couple) => couple.active);
  const ended = data.couples.filter((couple) => !couple.active);
  const todoOf = (couple: Couple) =>
  {
    const summary = data.summary.couples.find((candidate) => candidate.couple === couple.id);

    return summary ? todoItems(summary) : [];
  };

  return (
    <>
      <Notice />
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-4">
        <h1 className="h4 mb-0">Coppie</h1>
        <ButtonLink to="/inviti/nuovo">Invita una coppia</ButtonLink>
      </div>
      <section className="mb-4">
        <h2 className="h5">Percorsi in corso</h2>
        {active.length > 0 ? (
          active.map((couple) => <CoupleCard key={couple.id} couple={couple} todo={todoOf(couple)} />)
        ) : (
          <p className="text-body-secondary">Nessun percorso in corso.</p>
        )}
      </section>
      {data.summary.pendingInvitations.length > 0 && (
        <section className="mb-4">
          <h2 className="h5">Inviti in corso</h2>
          {data.summary.pendingInvitations.map((invitation) => (
            <SentInvitation key={invitation.id} invitation={invitation} />
          ))}
        </section>
      )}
      {ended.length > 0 && (
        <section>
          <h2 className="h5">Percorsi conclusi</h2>
          {ended.map((couple) => (
            <CoupleCard key={couple.id} couple={couple} />
          ))}
        </section>
      )}
    </>
  );
}

/** An invitation the therapist sent: who accepted, when it expires, and its withdrawal. */
function SentInvitation({ invitation }: { invitation: Invitation })
{
  const { api } = useApp();
  const [confirming, setConfirming] = useState(false);
  const withdraw = useCommand(() => ask(api.POST('/api/invitations/{id}/withdraw', { params: { path: { id: invitation.id } } })));
  const partners = [invitation.partnerOne, invitation.partnerTwo];
  const accepted = partners.filter((partner) => invitation.acceptedBy.includes(partner.id)).map(nameOf);
  const names = partnersOf(invitation);

  return (
    <Card body className="mb-2">
      <div className="fw-semibold">{names}</div>
      <div className="text-body-secondary small mb-2">
        {accepted.length > 0 ? `Ha accettato: ${accepted.join(' e ')}` : 'Nessuno ha ancora accettato'} · Scade il {formatDate(invitation.expiresAt)}
      </div>
      <Button variant="outline-danger" size="sm" onClick={() => setConfirming(true)} disabled={withdraw.pending}>
        Ritira l’invito
      </Button>
      <ErrorText error={withdraw.error} />
      <ConfirmDialog
        show={confirming}
        title="Ritirare l’invito?"
        text={`${names} non potranno più accettarlo.`}
        confirm="Ritira l’invito"
        cancel="Annulla"
        onConfirm={() =>
        {
          setConfirming(false);
          void withdraw.run();
        }}
        onCancel={() => setConfirming(false)}
      />
    </Card>
  );
}
