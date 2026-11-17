import { type FormEvent, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';

import { type Account, ask, type Closing, type Couple, type Declaration, type Limits } from '../api/client';
import { useApp } from '../app/context';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ErrorText } from '../components/ErrorText';
import { TextField, UserText } from '../components/TextField';
import { useCommand } from '../components/useCommand';
import { ABANDON_TEXT } from '../conversation/Composer';
import { nameIn, toWhom } from '../negotiations/words';
import { useUnsentText } from '../session/unsent';
import { formatDateTime } from '../texts/format';
import { CLOSING_REQUEST_STATUS_LABELS, OUTCOME_LABELS, THERAPIST_REQUEST_STATUS_LABELS } from '../texts/labels';
import { closureLine, declarationOf, history, pendingRequest, pendingTherapistRequest, therapistOption, toTherapist } from './closing';

/** What closing does, said before it is done (texts approved with F9). */
export const CLOSE_TEXT = 'Accettando, il discorso si chiude: nessuno dei due potrà più scrivere qui. Poi ognuno dichiarerà com’è andato. Non si può annullare.';
export const NEEDS_BOTH = 'Per chiudere il discorso serve l’accordo di entrambi.';

interface Props
{
  closing: Closing;
  couple: Couple;
  me: Account;
  limits: Limits;
  conversation: string;
  number: number;
}

type Outcome = Declaration['outcome'] & string;

function otherOf(couple: Couple, partner: string): string
{
  return couple.partnerOne.id === partner ? couple.partnerTwo.id : couple.partnerOne.id;
}

/**
 * The closing of a specific discussion (CLAUDE.md, F9): the requests between the partners, the special request to the
 * therapist, how it was closed and the outcomes. Only the actions possible now are offered; once the couple has ended
 * everything stays as it was, read-only.
 */
export function ClosingPanel({ closing, couple, me, limits, conversation, number }: Props)
{
  const lines = history(closing, couple, me, { request: CLOSING_REQUEST_STATUS_LABELS, therapist: THERAPIST_REQUEST_STATUS_LABELS });
  const pending = pendingRequest(closing) ?? pendingTherapistRequest(closing);

  // Nothing to say: nobody asked anything, and this person can't ask (the therapist, or anyone once the couple has ended).
  if (!closing.closed && !pending && lines.length === 0 && (me.id === couple.therapist.id || !couple.active))
  {
    return null;
  }

  return (
    <section className="mt-4" aria-labelledby="closing-title">
      <h2 id="closing-title" className="h5">Chiusura del discorso</h2>
      {closing.closed ? (
        <Closed closing={closing} couple={couple} me={me} limits={limits} conversation={conversation} number={number} />
      ) : (
        <Open closing={closing} couple={couple} me={me} limits={limits} conversation={conversation} number={number} />
      )}
      {lines.length > 0 && (
        <>
          <h3 className="h6 mt-3">Storico</h3>
          <ul className="list-unstyled small">
            {lines.map((line) => (
              <li key={line.key} className="mb-2">
                {line.text}
                {line.justification && <UserText text={line.justification} className="text-body-secondary mb-0" />}
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}

type Confirming = 'accept' | 'confirm';

function Open({ closing, couple, me, limits, conversation, number }: Props)
{
  const { api } = useApp();
  const [asking, setAsking] = useState(false);
  const [confirming, setConfirming] = useState<Confirming>();
  const path = { params: { path: { id: conversation, number } } };
  const request = useCommand(() => ask(api.POST('/api/conversations/{id}/discussions/{number}/closing/request', path)));
  const accept = useCommand(() => ask(api.POST('/api/conversations/{id}/discussions/{number}/closing/accept', path)));
  const refuse = useCommand(() => ask(api.POST('/api/conversations/{id}/discussions/{number}/closing/refuse', path)));
  const withdraw = useCommand(() => ask(api.POST('/api/conversations/{id}/discussions/{number}/closing/withdraw', path)));
  const confirm = useCommand(() => ask(api.POST('/api/conversations/{id}/discussions/{number}/closing/therapist-request/confirm', path)));
  const reject = useCommand(() => ask(api.POST('/api/conversations/{id}/discussions/{number}/closing/therapist-request/refuse', path)));
  const commands = [request, accept, refuse, withdraw, confirm, reject];
  const busy = commands.some((one) => one.pending);
  const error = commands.find((one) => one.error !== undefined)?.error;
  const isTherapist = me.id === couple.therapist.id;
  const waiting = pendingRequest(closing);
  const toDecide = pendingTherapistRequest(closing);
  const therapist = nameIn(couple, couple.therapist.id);
  const active = couple.active;
  let body;

  if (toDecide && isTherapist)
  {
    body = (
      <>
        <p className="fw-semibold">{nameIn(couple, toDecide.requester)} ti chiede di chiudere il discorso ({formatDateTime(toDecide.sentAt)}).</p>
        <div className="small text-body-secondary">Motivazione</div>
        <UserText text={toDecide.justification ?? ''} />
        <h3 className="h6">Richieste di chiusura precedenti</h3>
        <ul className="small">
          {toDecide.previousRequests.map((previous, index) => (
            <li key={index}>
              {`Richiesta di ${nameIn(couple, previous.requester)} · inviata il ${formatDateTime(previous.sentAt)} · ${CLOSING_REQUEST_STATUS_LABELS[previous.status]}`}
              {previous.endedAt ? ` il ${formatDateTime(previous.endedAt)}` : ''}
            </li>
          ))}
        </ul>
        {active && (
          <div className="d-flex flex-wrap gap-2">
            <Button onClick={() => setConfirming('confirm')} disabled={busy}>
              Chiudi il discorso
            </Button>
            <Button variant="outline-secondary" onClick={() => void reject.run()} disabled={busy}>
              Non chiudere
            </Button>
          </div>
        )}
      </>
    );
  }
  else if (toDecide)
  {
    const mine = toDecide.requester === me.id;

    body = (
      <>
        <p className="fw-semibold">
          {mine ? 'Hai chiesto' : `${nameIn(couple, toDecide.requester)} ha chiesto`} {toWhom(therapist)} di chiudere il discorso il{' '}
          {formatDateTime(toDecide.sentAt)}. In attesa della sua decisione.
        </p>
        {mine && toDecide.justification && (
          <>
            <div className="small text-body-secondary">La tua motivazione</div>
            <UserText text={toDecide.justification} />
          </>
        )}
      </>
    );
  }
  else if (waiting)
  {
    const mine = waiting.requester === me.id;
    const other = nameIn(couple, otherOf(couple, waiting.requester));

    if (isTherapist)
    {
      body = <p className="fw-semibold">{nameIn(couple, waiting.requester)} ha chiesto {toWhom(other)} di chiudere il discorso il {formatDateTime(waiting.sentAt)}.</p>;
    }
    else if (mine)
    {
      body = (
        <>
          <p className="fw-semibold">Hai chiesto {toWhom(other)} di chiudere il discorso il {formatDateTime(waiting.sentAt)}. In attesa della sua risposta.</p>
          {active && (
            <Button variant="outline-secondary" onClick={() => void withdraw.run()} disabled={busy}>
              Ritira la richiesta
            </Button>
          )}
        </>
      );
    }
    else
    {
      body = (
        <>
          <p className="fw-semibold">{nameIn(couple, waiting.requester)} chiede di chiudere il discorso ({formatDateTime(waiting.sentAt)}).</p>
          {active && (
            <div className="d-flex flex-wrap gap-2">
              <Button onClick={() => setConfirming('accept')} disabled={busy}>
                Accetta e chiudi
              </Button>
              <Button variant="outline-secondary" onClick={() => void refuse.run()} disabled={busy}>
                Rifiuta
              </Button>
            </div>
          )}
        </>
      );
    }
  }
  else if (!isTherapist && active)
  {
    const option = therapistOption(closing, me.id);
    const other = nameIn(couple, otherOf(couple, me.id));

    body = asking ? (
      <TherapistRequestForm couple={couple} me={me} limits={limits} conversation={conversation} number={number} onClose={() => setAsking(false)} />
    ) : (
      <>
        <p>{NEEDS_BOTH}</p>
        {option === 'allowed' && (
          <p>Hai già provato a chiuderlo con {other} senza riuscirci: puoi chiedere {toTherapist(couple)} di chiuderlo.</p>
        )}
        {option === 'retry' && <p>Per chiedere di nuovo {toTherapist(couple)}, prova prima ancora a chiuderlo con {other}.</p>}
        <div className="d-flex flex-wrap gap-2">
          <Button variant="outline-primary" onClick={() => void request.run()} disabled={busy}>
            Chiedi di chiudere il discorso
          </Button>
          {option === 'allowed' && (
            <Button variant="outline-secondary" onClick={() => setAsking(true)} disabled={busy}>
              Chiedi {toTherapist(couple)}
            </Button>
          )}
        </div>
      </>
    );
  }

  return (
    <>
      {body}
      <ErrorText error={error} />
      <ConfirmDialog
        show={confirming !== undefined}
        title="Chiudere il discorso?"
        text={CLOSE_TEXT}
        confirm="Chiudi il discorso"
        cancel="Annulla"
        onConfirm={() =>
        {
          const run = confirming === 'confirm' ? confirm.run : accept.run;
          setConfirming(undefined);
          void run();
        }}
        onCancel={() => setConfirming(undefined)}
      />
    </>
  );
}

/** The special request to the therapist, with its justification (unsent text until it is sent). */
function TherapistRequestForm({ couple, me, limits, conversation, number, onClose }: Omit<Props, 'closing'> & { onClose: () => void })
{
  const { api } = useApp();
  const [justification, setJustification] = useState('');
  const [giving, setGiving] = useState(false);
  const release = useUnsentText(justification !== '');
  const therapist = nameIn(couple, couple.therapist.id);
  const other = nameIn(couple, otherOf(couple, me.id));
  const send = useCommand(() => ask(api.POST('/api/conversations/{id}/discussions/{number}/closing/therapist-request', {
    params: { path: { id: conversation, number } },
    body: { justification },
  })), {
    onDone: () =>
    {
      release();
      onClose();
    },
  });

  const submit = (event: FormEvent) =>
  {
    event.preventDefault();

    if (justification.trim() === '')
    {
      send.setError('Manca la motivazione.');
    }
    else
    {
      void send.run();
    }
  };

  const cancel = () =>
  {
    if (justification === '')
    {
      onClose();
    }
    else
    {
      setGiving(true);
    }
  };

  return (
    <Form noValidate onSubmit={submit}>
      <TextField id="therapist-justification" label="Motivazione" value={justification} onChange={setJustification}
        maxLength={limits.therapistRequestJustification} rows={5} disabled={send.pending}
        hint={`${therapist} leggerà la motivazione e le richieste di chiusura fatte finora, con le loro date. ${other} saprà della richiesta, ma non leggerà la motivazione.`} />
      <div className="d-flex flex-wrap gap-2">
        <Button type="submit" disabled={send.pending}>
          Invia la richiesta
        </Button>
        <Button variant="secondary" onClick={cancel} disabled={send.pending}>
          Annulla
        </Button>
      </div>
      <ErrorText error={send.error} />
      <ConfirmDialog
        show={giving}
        text={ABANDON_TEXT}
        confirm="Rinuncia"
        cancel="Continua a scrivere"
        onConfirm={() =>
        {
          setGiving(false);
          release();
          onClose();
        }}
        onCancel={() => setGiving(false)}
      />
    </Form>
  );
}

function Closed({ closing, couple, me, limits, conversation, number }: Props)
{
  const isTherapist = me.id === couple.therapist.id;
  const ended = !couple.active;
  const missing = ended ? 'non ha dichiarato l’esito.' : 'non ha ancora dichiarato l’esito.';

  const shown = (declaration: Declaration, subject: string) => (
    <div className="mb-2">
      <div>
        {subject}: {declaration.outcome ? OUTCOME_LABELS[declaration.outcome] : ''} · {formatDateTime(declaration.declaredAt)}
      </div>
      <UserText text={declaration.reason ?? ''} className="text-body-secondary mb-0" />
    </div>
  );

  const ofPartner = (partner: string) =>
  {
    const state = declarationOf(closing, partner);
    const name = nameIn(couple, partner);

    switch (state.kind)
    {
      case 'shown':
        return shown(state.declaration, `Esito di ${name}`);
      case 'hidden':
        return <p>{name} ha dichiarato il suo esito: lo vedrai quando avrai dichiarato anche tu.</p>;
      case 'missing':
        return <p>{name} {missing}</p>;
    }
  };

  let declarations;

  if (isTherapist)
  {
    declarations = (
      <>
        <div key="one">{ofPartner(couple.partnerOne.id)}</div>
        <div key="two">{ofPartner(couple.partnerTwo.id)}</div>
      </>
    );
  }
  else
  {
    const mine = declarationOf(closing, me.id);
    const other = otherOf(couple, me.id);
    let own;

    if (mine.kind === 'shown')
    {
      own = shown(mine.declaration, 'Il tuo esito');
    }
    else if (ended)
    {
      own = <p>Non hai dichiarato l’esito.</p>;
    }
    else
    {
      own = <DeclarationForm couple={couple} me={me} limits={limits} conversation={conversation} number={number} />;
    }

    declarations = (
      <>
        {own}
        {ofPartner(other)}
      </>
    );
  }

  return (
    <>
      <p className="fw-semibold">{closureLine(closing, couple, me)}</p>
      <h3 className="h6">Esito</h3>
      {declarations}
    </>
  );
}

/** This partner's outcome: final once declared, so a dialog asks first. */
function DeclarationForm({ couple, me, limits, conversation, number }: Omit<Props, 'closing'>)
{
  const { api } = useApp();
  const [outcome, setOutcome] = useState<Outcome>();
  const [reason, setReason] = useState('');
  const [confirming, setConfirming] = useState(false);
  const release = useUnsentText(reason !== '' || outcome !== undefined);
  const other = nameIn(couple, otherOf(couple, me.id));
  const declare = useCommand(() => ask(api.POST('/api/conversations/{id}/discussions/{number}/closing/outcome', {
    params: { path: { id: conversation, number } },
    body: { outcome: outcome ?? 'RESOLVED', reason },
  })), { onDone: release });

  const submit = (event: FormEvent) =>
  {
    event.preventDefault();
    const problem = outcome === undefined ? 'Scegli un esito.' : reason.trim() === '' ? 'Manca il motivo.' : undefined;

    if (problem)
    {
      declare.setError(problem);
    }
    else
    {
      declare.setError(undefined);
      setConfirming(true);
    }
  };

  return (
    <Card body className="mb-3">
      <Form noValidate onSubmit={submit}>
        <fieldset className="mb-3">
          <legend className="fs-6">Com’è andato questo discorso?</legend>
          {(['RESOLVED', 'STALLED'] as const).map((value) => (
            <Form.Check key={value} type="radio" id={`outcome-${value}`} name="outcome" label={OUTCOME_LABELS[value]} checked={outcome === value}
              onChange={() => setOutcome(value)} disabled={declare.pending} />
          ))}
        </fieldset>
        <TextField id="outcome-reason" label="Motivo" value={reason} onChange={setReason} maxLength={limits.outcomeReason} rows={2}
          hint="Una frase breve: perché lo consideri risolto o arenato." disabled={declare.pending} />
        <p className="small text-body-secondary">Vedrai l’esito di {other} quando avrai dichiarato anche tu.</p>
        <Button type="submit" disabled={declare.pending}>
          Dichiara l’esito
        </Button>
        <ErrorText error={declare.error} />
      </Form>
      <ConfirmDialog
        show={confirming}
        title="Dichiarare l’esito?"
        text={`Esito: ${outcome ? OUTCOME_LABELS[outcome] : ''}. Una volta dichiarato non si può più modificare.`}
        confirm="Dichiara"
        cancel="Annulla"
        onConfirm={() =>
        {
          setConfirming(false);
          void declare.run();
        }}
        onCancel={() => setConfirming(false)}
      />
    </Card>
  );
}
