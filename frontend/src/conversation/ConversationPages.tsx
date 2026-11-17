import { useEffect, useMemo, useReducer, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import { Link, useLoaderData, useLocation, useParams } from 'react-router';

import type { Account, Closing, Constraint, Conversation, Couple, Limits, Negotiation, Paragraph } from '../api/client';
import { useAccount } from '../app/context';
import { ClosingPanel } from '../closings/ClosingPanel';
import { ErrorText } from '../components/ErrorText';
import { ButtonLink, Notice } from '../components/frames';
import { UserText } from '../components/TextField';
import { partnersOf } from '../couples/CoupleCard';
import { FROM_A_PARAGRAPH } from '../negotiations/NewNegotiationPages';
import { nameIn, toWhom } from '../negotiations/words';
import { ERROR_TEXTS } from '../texts/errors';
import { formatDate, formatTime } from '../texts/format';
import { Composer, positionFor } from './Composer';
import { answeredInDraft, answeringIn, type DraftAction, draftReducer, expandedElsewhere } from './draft';
import { indexParagraphs, Letter, MAIN_DISCUSSION, ParagraphBlock, paragraphAddress } from './Letters';

export { MAIN_DISCUSSION };
import { constraintsFor, nextChange, placeWords, writeStatus, type WriteStatus } from './rules';

export interface ConversationData
{
  conversation: Conversation;
  couple: Couple;
}

export interface DiscussionData extends ConversationData
{
  inForce: Constraint[];
  limits: Limits;
  /** The stipule in progress: a paragraph whose expansion is requested shows it (F8). */
  negotiations: Negotiation[];
  /** The closing of a specific discussion (F9); the main one has none. */
  closing?: Closing;
}

export const NO_CONVERSATION = 'Non c’è una conversazione in corso.';

/** /conversazione, the patient's bar link, when the couple in progress has no conversation (or there is no couple). */
export function NoConversationPage()
{
  return (
    <>
      <h1 className="h4 mb-3">Conversazione</h1>
      <p>{NO_CONVERSATION}</p>
    </>
  );
}

function isPartner(couple: Couple, me: Account | null): boolean
{
  return me !== null && (me.id === couple.partnerOne.id || me.id === couple.partnerTwo.id);
}

function countWords(count: number, unopened: number): string
{
  if (count === 0)
  {
    return 'Ancora nessun messaggio.';
  }

  const messages = count === 1 ? '1 messaggio' : `${count} messaggi`;

  return unopened > 0 ? `${messages} · ${unopened} da aprire` : messages;
}

/**
 * /conversazioni/:id (CLAUDE.md, F7): the agreement in force and the discussions, the main one first, each with its
 * number of messages and, for a partner, how many are still to open.
 */
export function ConversationPage()
{
  const { conversation, couple } = useLoaderData() as ConversationData;
  const me = useAccount();

  return (
    <>
      <p className="mb-2">
        <Link to={`/coppie/${couple.id}`}>Torna alla coppia</Link>
      </p>
      <Notice />
      <h1 className="h4 mb-1">Conversazione</h1>
      <p className="text-body-secondary">{partnersOf(couple)}</p>
      <section className="mb-4">
        <h2 className="h5">L’accordo in vigore</h2>
        <Card body>
          <UserText text={conversation.agreement} className="mb-0" />
        </Card>
      </section>
      <section className="mb-4">
        <h2 className="h5">Discorsi</h2>
        {conversation.discussions.map((discussion) =>
        {
          const unopened = discussion.messages.filter((message) => message.closedEnvelope).length;

          return (
            <Card body key={discussion.id} className="mb-2">
              <Link to={`/conversazioni/${conversation.id}/discorsi/${discussion.id}`} className="fw-semibold">
                {discussion.title ?? MAIN_DISCUSSION}
              </Link>
              {discussion.closed && <span className="badge text-bg-secondary ms-2">Chiuso</span>}
              {discussion.subtitle && <UserText text={discussion.subtitle} className="small mb-1" />}
              <div className="small text-body-secondary">{countWords(discussion.messages.length, unopened)}</div>
            </Card>
          );
        })}
      </section>
      <div className="d-flex flex-wrap gap-2">
        {isPartner(couple, me) && couple.active && (
          <ButtonLink to={`/conversazioni/${conversation.id}/nuovo-discorso`} variant="outline-primary">
            Proponi un discorso specifico
          </ButtonLink>
        )}
        <ButtonLink to={`/conversazioni/${conversation.id}/vincoli`} variant="outline-secondary">
          Vincoli
        </ButtonLink>
      </div>
    </>
  );
}

/** The status line (F7 decision 4; texts approved with F7). */
export function statusText(status: WriteStatus, couple: Couple): string
{
  switch (status.kind)
  {
    case 'ended':
      return ERROR_TEXTS.COUPLE_ENDED;
    case 'closed':
      return ERROR_TEXTS.DISCUSSION_CLOSED;
    case 'therapist':
      return status.next ? `Tocca ${toWhom(nameIn(couple, status.next))}.` : 'Si aspetta il primo messaggio.';
    case 'mine':
      return `Hai inviato l’ultimo messaggio: ora tocca ${toWhom(nameIn(couple, status.next))}.`;
    case 'unopened':
      return `Apri il messaggio di ${nameIn(couple, status.sender)} per poter rispondere.`;
    case 'waiting':
      return `Potrai rispondere dalle ${formatTime(status.until)} del ${formatDate(status.until.toISOString())}.`;
    case 'time':
      return `Potrai scrivere dalle ${status.from}.`;
    case 'location':
      return `Per scrivere dovrai trovarti ${placeWords(status.constraint)}: l’app lo verificherà quando inizierai a scrivere.`;
    case 'can':
      return status.first ? 'Puoi scrivere il primo messaggio.' : 'Tocca a te: puoi rispondere.';
  }
}

/**
 * The time on screen, moved by a local timer (F7 decision 4): every minute, for the time constraints, and exactly at the
 * end of a wait. The server is never asked.
 */
function useNow(next: (now: Date) => Date | undefined): Date
{
  const [now, setNow] = useState(() => new Date());
  const end = next(now)?.getTime();

  useEffect(() =>
  {
    const tick = () => setNow(new Date());
    const minute = window.setInterval(tick, 60_000);
    const timer = end === undefined ? undefined : window.setTimeout(tick, Math.max(0, end - Date.now()) + 100);

    return () =>
    {
      window.clearInterval(minute);
      window.clearTimeout(timer);
    };
  }, [end]);

  return now;
}

/**
 * /conversazioni/:id/discorsi/:n (CLAUDE.md, F7): the letters of a discussion in order, the closed envelopes, and the line
 * saying where things stand. F8 adds the reply.
 */
export function DiscussionPage()
{
  const { conversation, couple, inForce, limits, negotiations, closing } = useLoaderData() as DiscussionData;
  const me = useAccount();
  const { hash } = useLocation();
  const number = Number(useParams().n);
  const discussion = conversation.discussions.find((candidate) => candidate.id === number);
  const index = useMemo(() => indexParagraphs(conversation), [conversation]);
  const now = useNow((at) => (discussion && me ? nextChange(writeStatus(discussion, couple, me, inForce, at)) : undefined));
  const [highlighted, setHighlighted] = useState<string>();
  const [writing, setWriting] = useState(false);
  const [cards, dispatch] = useReducer(draftReducer, []);
  const [startProblem, setStartProblem] = useState<string>();

  // A link to a paragraph scrolls to it and highlights it for a moment (F7 decision 1).
  useEffect(() =>
  {
    const target = hash.startsWith('#p-') ? hash.slice(3) : undefined;

    if (!target)
    {
      return undefined;
    }

    document.getElementById(`p-${target}`)?.scrollIntoView?.({ block: 'center' });
    const show = window.setTimeout(() => setHighlighted(target), 0);
    const hide = window.setTimeout(() => setHighlighted(undefined), 2500);

    return () =>
    {
      window.clearTimeout(show);
      window.clearTimeout(hide);
    };
  }, [hash, conversation]);

  if (!discussion || !me)
  {
    return null;
  }

  const origin = discussion.originParagraphId ? index.get(discussion.originParagraphId) : undefined;
  const status = writeStatus(discussion, couple, me, inForce, now);
  const answering = answeringIn(discussion, conversation);
  const canWrite = status.kind === 'can' || status.kind === 'location';
  const places = constraintsFor(inForce, me.id, 'WRITE').filter((constraint) => constraint.type === 'LOCATION');

  // The writing starts only when every WRITE constraint is satisfied (frontend point 6): a place is checked here, in the
  // browser, at the click; nothing is sent (F8 decision 2).
  const startWriting = async (then?: DraftAction) =>
  {
    if (!writing)
    {
      const { problem } = await positionFor(places, 'scrivere');
      setStartProblem(problem);

      if (problem)
      {
        return;
      }

      setWriting(true);
    }

    if (then)
    {
      dispatch(then);
    }
  };

  const actions = (paragraph: Paragraph) =>
  {
    const elsewhere = expandedElsewhere(conversation, paragraph.id, discussion.id);

    if (elsewhere)
    {
      return (
        <div className="small">
          Se ne parla in <Link to={`/conversazioni/${conversation.id}/discorsi/${elsewhere.id}`}>«{elsewhere.title ?? MAIN_DISCUSSION}»</Link>
        </div>
      );
    }

    if (!canWrite || !answering.paragraphs.some((candidate) => candidate.id === paragraph.id))
    {
      return null;
    }

    const requested = negotiations.some((negotiation) => negotiation.originParagraphId === paragraph.id);

    return (
      <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
        {!(paragraph.type === 'QUESTION' && answeredInDraft(cards, paragraph.id)) && (
          <Button size="sm" variant="outline-primary" onClick={() => void startWriting({ kind: 'reply', to: paragraph, answering })}>
            Rispondi
          </Button>
        )}
        {!answering.origin && (requested ? (
          <span className="small text-body-secondary">Richiesta di approfondimento in corso</span>
        ) : (
          <ButtonLink to={`/conversazioni/${conversation.id}/nuovo-discorso?paragrafo=${paragraph.id}`} variant="outline-secondary btn-sm">
            Approfondisci
          </ButtonLink>
        ))}
      </div>
    );
  };

  return (
    <>
      <p className="mb-2">
        <Link to={`/conversazioni/${conversation.id}`}>Torna alla conversazione</Link>
      </p>
      <Notice />
      <h1 className="h4 mb-1">{discussion.title ?? MAIN_DISCUSSION}</h1>
      {discussion.subtitle && <UserText text={discussion.subtitle} className="text-body-secondary mb-1" />}
      {discussion.originParagraphId && (
        <p className="small text-body-secondary">
          {origin && discussion.originParagraphId ? (
            <>
              Nasce da <Link to={paragraphAddress(conversation.id, origin.discussion, discussion.originParagraphId)}>«{origin.title}»</Link>
            </>
          ) : (
            FROM_A_PARAGRAPH
          )}
        </p>
      )}
      <Row>
        <Col lg={writing ? 6 : 12}>
          <section className="mt-3">
            {discussion.messages.length === 0 && <p className="text-body-secondary">Ancora nessun messaggio.</p>}
            {answering.origin && (
              <Card body className="mb-3">
                <ParagraphBlock paragraph={answering.origin} conversation={conversation.id} index={index} highlighted={false} actions={actions} />
              </Card>
            )}
            {discussion.messages.map((message) => (
              <Letter key={message.id} message={message} conversation={conversation} discussion={discussion.id} couple={couple} me={me} index={index}
                inForce={inForce} now={now} highlighted={highlighted} actions={actions} />
            ))}
          </section>
          <p className="fw-semibold" role="status">
            {statusText(status, couple)}
          </p>
          {canWrite && !writing && (
            <Button onClick={() => void startWriting()}>{discussion.messages.length === 0 ? 'Scrivi il primo messaggio' : 'Scrivi la risposta'}</Button>
          )}
          <ErrorText error={startProblem} />
          {closing && <ClosingPanel closing={closing} couple={couple} me={me} limits={limits} conversation={conversation.id} number={discussion.id} />}
        </Col>
        {writing && (
          <Col lg={6} className="mt-3">
            <Composer cards={cards} dispatch={dispatch} answering={answering} conversation={conversation} discussion={discussion.id} couple={couple}
              me={me} limits={limits} places={places} onClose={() => setWriting(false)} />
          </Col>
        )}
      </Row>
    </>
  );
}
