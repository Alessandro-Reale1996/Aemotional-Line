import { type FormEvent, useReducer, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Modal from 'react-bootstrap/Modal';
import Row from 'react-bootstrap/Row';
import { Link, useLoaderData } from 'react-router';

import { type Account, ask, type Constraint, type ConstraintRequest, type Constraints, type ConstraintSpec, type Couple, type Limits } from '../api/client';
import { useAccount, useApp } from '../app/context';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ErrorText } from '../components/ErrorText';
import { Notice } from '../components/frames';
import { useCommand } from '../components/useCommand';
import { ABANDON_TEXT } from '../conversation/Composer';
import { partnersOf } from '../couples/CoupleCard';
import { nameIn } from '../negotiations/words';
import { useUnsentText } from '../session/unsent';
import { formatDate, formatDateTime } from '../texts/format';
import { CONSTRAINT_REQUEST_STATUS_LABELS, CONSTRAINT_TYPE_LABELS, DIRECTION_LABELS, SCOPE_LABELS } from '../texts/labels';
import {
  asConstraint,
  cardOf,
  cardsReducer,
  type ConstraintCard,
  type ConstraintType,
  DELAY_UNITS,
  type DelayUnit,
  type Direction,
  placeOf,
  proposalProblem,
  type Scope,
  specOf,
} from './form';
import { approvalLine, approveText, byPartner, constraintSentence, coordinates, mapLink, proposedBy } from './words';

export interface ConstraintsData
{
  conversation: string;
  couple: Couple;
  constraints: Constraints;
  limits: Limits;
}

/** Texts approved with F9. */
export const NONE_IN_FORCE = 'Nessun vincolo in vigore.';
export const LIFTS_ALL = 'Nessun vincolo: la proposta li toglie tutti.';
export const REJECT_TEXT = 'Rifiutando, la proposta si chiude e restano in vigore i vincoli attuali. Non si può annullare.';
export const WITHDRAW_TEXT = 'Ritirando, la proposta si chiude e restano in vigore i vincoli attuali. Non si può annullare.';
export const WAIT_FOR_PENDING = 'Potrai proporre nuovi vincoli quando la proposta in attesa sarà chiusa.';

function isPartner(couple: Couple, me: Account): boolean
{
  return me.id === couple.partnerOne.id || me.id === couple.partnerTwo.id;
}

/**
 * /conversazioni/:id/vincoli (CLAUDE.md, F9): the constraints in force and every request. The therapist proposes the
 * whole new set; it takes effect once both partners have approved it.
 */
export function ConstraintsPage()
{
  const { conversation, couple, constraints, limits } = useLoaderData() as ConstraintsData;
  const me = useAccount();
  const [proposing, setProposing] = useState(false);

  if (!me)
  {
    return null;
  }

  const pending = constraints.requests.find((request) => request.status === 'PENDING');
  const previous = constraints.requests.filter((request) => request.status !== 'PENDING');
  const therapist = nameIn(couple, couple.therapist.id);
  const canPropose = me.id === couple.therapist.id && couple.active;

  return (
    <>
      <p className="mb-2">
        <Link to={`/conversazioni/${conversation}`}>Torna alla conversazione</Link>
      </p>
      <Notice />
      <h1 className="h4 mb-1">Vincoli</h1>
      <p className="text-body-secondary">{partnersOf(couple)}</p>
      <p>
        I vincoli limitano quando e da dove ciascuno può aprire i messaggi o scrivere. Li propone {therapist}; entrano in vigore solo quando
        entrambi li hanno approvati.
      </p>
      <section className="mb-4">
        <h2 className="h5">In vigore</h2>
        <ConstraintList constraints={constraints.inForce} couple={couple} me={me} empty={NONE_IN_FORCE} />
        {canPropose && !pending && !proposing && <Button onClick={() => setProposing(true)}>Proponi nuovi vincoli</Button>}
        {canPropose && pending && <p className="text-body-secondary">{WAIT_FOR_PENDING}</p>}
      </section>
      {proposing && (
        <ProposalForm conversation={conversation} couple={couple} me={me} inForce={constraints.inForce} limits={limits} onClose={() => setProposing(false)} />
      )}
      {pending && (
        <section className="mb-4">
          <h2 className="h5">Proposta in attesa</h2>
          <RequestCard request={pending} conversation={conversation} couple={couple} me={me} />
        </section>
      )}
      {previous.length > 0 && (
        <section className="mb-4">
          <h2 className="h5">Proposte precedenti</h2>
          {previous.map((request) => (
            <RequestCard key={request.id} request={request} conversation={conversation} couple={couple} me={me} />
          ))}
        </section>
      )}
    </>
  );
}

/** Constraints in words, ordered by partner; the place on a map for whoever may see it, its numbers for the therapist. */
function ConstraintList({ constraints, couple, me, empty }: { constraints: Constraint[]; couple: Couple; me: Account; empty: string })
{
  if (constraints.length === 0)
  {
    return <p>{empty}</p>;
  }

  return (
    <ul>
      {byPartner(constraints, couple).map((constraint, index) => (
        <li key={index} className="mb-1">
          {constraintSentence(constraint, couple, me)}
          {constraint.latitude !== null && constraint.longitude !== null && (
            <div className="small">
              {me.id === couple.therapist.id && <span className="text-body-secondary me-2">{coordinates(constraint.latitude, constraint.longitude)}</span>}
              <a href={mapLink(constraint.latitude, constraint.longitude)} target="_blank" rel="noopener noreferrer">
                Apri su OpenStreetMap
              </a>
            </div>
          )}
        </li>
      ))}
    </ul>
  );
}

type Confirming = 'approve' | 'reject' | 'withdraw';

function RequestCard({ request, conversation, couple, me }: { request: ConstraintRequest; conversation: string; couple: Couple; me: Account })
{
  const { api } = useApp();
  const [confirming, setConfirming] = useState<Confirming>();
  const path = { params: { path: { id: conversation, requestId: request.id } } };
  const approve = useCommand(() => ask(api.POST('/api/conversations/{id}/constraints/requests/{requestId}/approve', path)));
  const reject = useCommand(() => ask(api.POST('/api/conversations/{id}/constraints/requests/{requestId}/reject', path)));
  const withdraw = useCommand(() => ask(api.POST('/api/conversations/{id}/constraints/requests/{requestId}/withdraw', path)));
  const busy = approve.pending || reject.pending || withdraw.pending;
  const error = approve.error ?? reject.error ?? withdraw.error;
  const open = request.status === 'PENDING' && couple.active;
  const partner = isPartner(couple, me);
  const approved = request.approvals.includes(me.id);
  const dialogs: Record<Confirming, { title: string; text: string; confirm: string; run: () => Promise<void> }> = {
    approve: { title: 'Approvare i vincoli?', text: approveText(request, couple, me), confirm: 'Approva', run: approve.run },
    reject: { title: 'Rifiutare la proposta?', text: REJECT_TEXT, confirm: 'Rifiuta la proposta', run: reject.run },
    withdraw: { title: 'Ritirare la proposta?', text: WITHDRAW_TEXT, confirm: 'Ritira la proposta', run: withdraw.run },
  };
  const dialog = confirming ? dialogs[confirming] : undefined;
  let details;

  switch (request.status)
  {
    case 'PENDING':
      details = approvalLine(request, couple, me);
      break;
    case 'APPLIED':
      details = `Approvata da entrambi il ${formatDateTime(request.closedAt ?? request.proposedAt)}.`;
      break;
    case 'REJECTED':
      details = `Rifiutata da ${request.rejectedBy === me.id ? 'te' : nameIn(couple, request.rejectedBy)} il ${formatDateTime(request.closedAt ?? request.proposedAt)}.`;
      break;
    case 'WITHDRAWN':
      details = `Ritirata il ${formatDateTime(request.closedAt ?? request.proposedAt)}.`;
      break;
  }

  return (
    <Card as="section" className={`mb-3 ${request.status === 'PENDING' ? 'border-primary' : ''}`.trim()} aria-label={`${proposedBy(request, couple, me)} del ${formatDate(request.proposedAt)}`}>
      <Card.Header className="small d-flex flex-wrap gap-2 align-items-center">
        <span>
          {proposedBy(request, couple, me)} del {formatDate(request.proposedAt)}
        </span>
        <span className="badge text-bg-secondary">{CONSTRAINT_REQUEST_STATUS_LABELS[request.status]}</span>
      </Card.Header>
      <Card.Body>
        <ConstraintList constraints={request.assignments} couple={couple} me={me} empty={LIFTS_ALL} />
        {request.status === 'APPLIED' && request.previousAssignments && (
          <>
            <div className="small text-body-secondary">Sostituiva:</div>
            <ConstraintList constraints={request.previousAssignments} couple={couple} me={me} empty="Nessun vincolo." />
          </>
        )}
        <p className="mb-2">{details}</p>
        {open && (
          <div className="d-flex flex-wrap gap-2">
            {partner && !approved && (
              <Button onClick={() => setConfirming('approve')} disabled={busy}>
                Approva
              </Button>
            )}
            {partner && (
              <Button variant="outline-danger" onClick={() => setConfirming('reject')} disabled={busy}>
                Rifiuta
              </Button>
            )}
            {request.proposedBy === me.id && (
              <Button variant="outline-danger" onClick={() => setConfirming('withdraw')} disabled={busy}>
                Ritira la proposta
              </Button>
            )}
          </div>
        )}
        <ErrorText error={error} />
      </Card.Body>
      <ConfirmDialog
        show={dialog !== undefined}
        title={dialog?.title ?? ''}
        text={dialog?.text ?? ''}
        confirm={dialog?.confirm ?? ''}
        cancel="Annulla"
        onConfirm={() =>
        {
          setConfirming(undefined);
          void dialog?.run();
        }}
        onCancel={() => setConfirming(undefined)}
      />
    </Card>
  );
}

interface FormProps
{
  conversation: string;
  couple: Couple;
  me: Account;
  inForce: Constraint[];
  limits: Limits;
  onClose: () => void;
}

/**
 * The therapist's proposal (CLAUDE.md, F9): it starts from the constraints in force, checks every card before the review,
 * and holds unsent text while it differs from them.
 */
function ProposalForm({ conversation, couple, me, inForce, limits, onClose }: FormProps)
{
  const { api } = useApp();
  const [cards, dispatch] = useReducer(cardsReducer, inForce, (constraints) => byPartner(constraints, couple).map((constraint) => cardOf(constraint, couple)));
  const [changed, setChanged] = useState(false);
  const [problem, setProblem] = useState<string>();
  const [reviewing, setReviewing] = useState<ConstraintSpec[]>();
  const [giving, setGiving] = useState(false);
  const release = useUnsentText(changed);
  const propose = useCommand((constraints: ConstraintSpec[]) =>
    ask(api.POST('/api/conversations/{id}/constraints/requests', { params: { path: { id: conversation } }, body: { constraints } })), {
    onDone: () =>
    {
      release();
      onClose();
    },
  });

  const edit = (card: ConstraintCard) => (change: Partial<Omit<ConstraintCard, 'key'>>) =>
  {
    setChanged(true);
    dispatch({ kind: 'edit', key: card.key, change });
  };

  const review = (event: FormEvent) =>
  {
    event.preventDefault();
    const found = proposalProblem(cards, limits.replyDelayMaxDays);
    setProblem(found);

    if (!found)
    {
      setReviewing(cards.map(specOf));
    }
  };

  const giveUp = () =>
  {
    if (changed)
    {
      setGiving(true);
    }
    else
    {
      onClose();
    }
  };

  return (
    <section className="mb-4" aria-labelledby="proposal-title">
      <h2 id="proposal-title" className="h5">Nuova proposta</h2>
      <p>La proposta sostituisce tutti i vincoli in vigore: parti da quelli attuali e cambia ciò che serve.</p>
      <Form noValidate onSubmit={review}>
        {cards.length === 0 && <p className="text-body-secondary">{LIFTS_ALL}</p>}
        {cards.map((card, index) => (
          <CardFields key={card.key} card={card} number={index + 1} couple={couple} maxDelayDays={limits.replyDelayMaxDays} onChange={edit(card)}
            onRemove={() =>
            {
              setChanged(true);
              dispatch({ kind: 'remove', key: card.key });
            }} />
        ))}
        <div className="d-flex flex-wrap gap-2">
          <Button variant="outline-primary" onClick={() =>
          {
            setChanged(true);
            dispatch({ kind: 'add', couple });
          }}>
            Aggiungi un vincolo
          </Button>
          <Button type="submit">Rivedi e proponi</Button>
          <Button variant="outline-danger" onClick={giveUp}>
            Rinuncia alla proposta
          </Button>
        </div>
        <ErrorText error={problem} />
      </Form>
      {reviewing && (
        <Modal show onHide={() => setReviewing(undefined)} size="lg" scrollable aria-labelledby="constraints-review-title">
          <Modal.Header closeButton closeLabel="Chiudi">
            <Modal.Title id="constraints-review-title" className="h5">
              Proporre questi vincoli?
            </Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <ConstraintList constraints={reviewing.map(asConstraint)} couple={couple} me={me} empty={LIFTS_ALL} />
            <p className="mb-0">
              Entreranno in vigore quando {partnersOf(couple)} li avranno approvati entrambi.
            </p>
            <ErrorText error={propose.error} />
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" onClick={() => setReviewing(undefined)}>
              Torna a modificare
            </Button>
            <Button onClick={() => void propose.run(reviewing)} disabled={propose.pending}>
              Proponi
            </Button>
          </Modal.Footer>
        </Modal>
      )}
      <ConfirmDialog
        show={giving}
        text={ABANDON_TEXT}
        confirm="Rinuncia"
        cancel="Continua a modificare"
        onConfirm={() =>
        {
          setGiving(false);
          release();
          onClose();
        }}
        onCancel={() => setGiving(false)}
      />
    </section>
  );
}

interface CardProps
{
  card: ConstraintCard;
  number: number;
  couple: Couple;
  maxDelayDays: number;
  onChange: (change: Partial<Omit<ConstraintCard, 'key'>>) => void;
  onRemove: () => void;
}

/** One constraint of the proposal: whom it binds, its type and the fields of its type. */
function CardFields({ card, number, couple, maxDelayDays, onChange, onRemove }: CardProps)
{
  const id = (field: string) => `constraint-${card.key}-${field}`;
  const place = placeOf(card);
  const scopes = (Object.keys(SCOPE_LABELS) as Scope[]).map((scope) => (
    <option key={scope} value={scope}>
      {SCOPE_LABELS[scope]}
    </option>
  ));

  return (
    <Card as="section" className="mb-3" aria-label={`Vincolo ${number}`}>
      <Card.Header className="small">Vincolo {number}</Card.Header>
      <Card.Body>
        <Row>
          <Form.Group as={Col} sm={6} className="mb-3" controlId={id('assignee')}>
            <Form.Label>Per</Form.Label>
            <Form.Select value={card.assignee} onChange={(event) => onChange({ assignee: event.target.value })}>
              {[couple.partnerOne, couple.partnerTwo].map((partner) => (
                <option key={partner.id} value={partner.id}>
                  {nameIn(couple, partner.id)}
                </option>
              ))}
            </Form.Select>
          </Form.Group>
          <Form.Group as={Col} sm={6} className="mb-3" controlId={id('type')}>
            <Form.Label>Tipo</Form.Label>
            <Form.Select value={card.type} onChange={(event) => onChange({ type: event.target.value as ConstraintType })}>
              {(Object.keys(CONSTRAINT_TYPE_LABELS) as ConstraintType[]).map((type) => (
                <option key={type} value={type}>
                  {CONSTRAINT_TYPE_LABELS[type]}
                </option>
              ))}
            </Form.Select>
          </Form.Group>
        </Row>
        {card.type === 'TIME' && (
          <Row>
            <Form.Group as={Col} sm={6} className="mb-3" controlId={id('not-before')}>
              <Form.Label>Dalle</Form.Label>
              <Form.Control type="time" value={card.notBefore} autoComplete="off" onChange={(event) => onChange({ notBefore: event.target.value })} />
            </Form.Group>
            <Form.Group as={Col} sm={6} className="mb-3" controlId={id('time-scope')}>
              <Form.Label>Vale per</Form.Label>
              <Form.Select value={card.timeScope} onChange={(event) => onChange({ timeScope: event.target.value as Scope })}>
                {scopes}
              </Form.Select>
            </Form.Group>
          </Row>
        )}
        {card.type === 'REPLY_DELAY' && (
          <Row>
            <Form.Group as={Col} sm={6} className="mb-1" controlId={id('delay')}>
              <Form.Label>Durata</Form.Label>
              <Form.Control inputMode="numeric" value={card.delayAmount} autoComplete="off" onChange={(event) => onChange({ delayAmount: event.target.value })} />
            </Form.Group>
            <Form.Group as={Col} sm={6} className="mb-1" controlId={id('delay-unit')}>
              <Form.Label>Unità</Form.Label>
              <Form.Select value={card.delayUnit} onChange={(event) => onChange({ delayUnit: event.target.value as DelayUnit })}>
                {(Object.keys(DELAY_UNITS) as DelayUnit[]).map((unit) => (
                  <option key={unit} value={unit}>
                    {DELAY_UNITS[unit].label}
                  </option>
                ))}
              </Form.Select>
            </Form.Group>
            <Form.Text muted className="mb-3">
              Al massimo {maxDelayDays} giorni. Vale sempre per la scrittura.
            </Form.Text>
          </Row>
        )}
        {card.type === 'LOCATION' && (
          <>
            <Row>
              <Form.Group as={Col} sm={6} className="mb-1" controlId={id('latitude')}>
                <Form.Label>Latitudine</Form.Label>
                <Form.Control inputMode="decimal" value={card.latitude} autoComplete="off" onChange={(event) => onChange({ latitude: event.target.value })} />
              </Form.Group>
              <Form.Group as={Col} sm={6} className="mb-1" controlId={id('longitude')}>
                <Form.Label>Longitudine</Form.Label>
                <Form.Control inputMode="decimal" value={card.longitude} autoComplete="off" onChange={(event) => onChange({ longitude: event.target.value })} />
              </Form.Group>
            </Row>
            <Form.Text muted className="d-block mb-2">
              In gradi decimali, per esempio 45,4642 e 9,1900. Su OpenStreetMap: clic destro sul punto, «Mostra indirizzo».
            </Form.Text>
            {place && (
              <p className="small">
                <a href={mapLink(place.latitude, place.longitude)} target="_blank" rel="noopener noreferrer">
                  Apri su OpenStreetMap
                </a>
              </p>
            )}
            <Row>
              <Form.Group as={Col} sm={4} className="mb-3" controlId={id('radius')}>
                <Form.Label>Raggio in metri</Form.Label>
                <Form.Control inputMode="decimal" value={card.radius} autoComplete="off" onChange={(event) => onChange({ radius: event.target.value })} />
              </Form.Group>
              <Form.Group as={Col} sm={4} className="mb-3" controlId={id('direction')}>
                <Form.Label>Direzione</Form.Label>
                <Form.Select value={card.direction} onChange={(event) => onChange({ direction: event.target.value as Direction })}>
                  {(Object.keys(DIRECTION_LABELS) as Direction[]).map((direction) => (
                    <option key={direction} value={direction}>
                      {DIRECTION_LABELS[direction]}
                    </option>
                  ))}
                </Form.Select>
              </Form.Group>
              <Form.Group as={Col} sm={4} className="mb-3" controlId={id('place-scope')}>
                <Form.Label>Vale per</Form.Label>
                <Form.Select value={card.placeScope} onChange={(event) => onChange({ placeScope: event.target.value as Scope })}>
                  {scopes}
                </Form.Select>
              </Form.Group>
            </Row>
          </>
        )}
      </Card.Body>
      <Card.Footer>
        <Button variant="outline-danger" size="sm" onClick={onRemove}>
          Rimuovi
        </Button>
      </Card.Footer>
    </Card>
  );
}
