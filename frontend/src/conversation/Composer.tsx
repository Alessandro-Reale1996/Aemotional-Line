import { type Dispatch, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Dropdown from 'react-bootstrap/Dropdown';
import Modal from 'react-bootstrap/Modal';
import { Link } from 'react-router';

import { type Account, ask, type Constraint, type Conversation, type Couple, type Limits } from '../api/client';
import { useApp } from '../app/context';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ErrorText } from '../components/ErrorText';
import { TextField, UserText } from '../components/TextField';
import { useCommand } from '../components/useCommand';
import { nameIn } from '../negotiations/words';
import { useUnsentText } from '../session/unsent';
import { PARAGRAPH_TYPE_LABELS } from '../texts/labels';
import {
  type Answering,
  type Card as DraftCard,
  citable,
  type DraftAction,
  draftProblem,
  findParagraph,
  fixedLink,
  graphOf,
  holdsText,
  linkOptions,
  MAX_CARDS,
  questionCount,
  toParagraphDrafts,
  topicOf,
} from './draft';
import { MAIN_DISCUSSION } from './Letters';
import { currentPosition, PositionError } from './position';
import { type Coordinates, localTime, placeWords, satisfiesLocation } from './rules';

export const IRREVERSIBLE = 'Una volta inviato, il messaggio non si può più modificare.';
export const ABANDON_TEXT = 'Rinunciando, il testo che hai scritto andrà perso.';
export const MAX_REACHED = `Un messaggio può avere al massimo ${MAX_CARDS} paragrafi.`;
/** When the page doesn't know the discussion yet: it was opened after the page was read. */
export const ANSWERED_ELSEWHERE = 'Un paragrafo a cui rispondi si approfondisce in un altro discorso: ricarica la pagina per vederlo.';

/** What a position check says when it fails: outside the place, denied, or unavailable (texts approved with F8). */
export async function positionFor(places: Constraint[], action: 'aprirlo' | 'scrivere'): Promise<{ position: Coordinates | null; problem?: string }>
{
  if (places.length === 0)
  {
    return { position: null };
  }

  let position: Coordinates;

  try
  {
    position = await currentPosition();
  }
  catch (failure)
  {
    const denied = failure instanceof PositionError && failure.reason === 'denied';

    return {
      position: null,
      problem: denied
        ? `Per ${action} serve la tua posizione: consentila a questo sito nelle impostazioni del browser e riprova.`
        : 'Non è stato possibile leggere la tua posizione. Riprova tra poco.',
    };
  }

  const unmet = places.find((constraint) => !satisfiesLocation(position, constraint));

  return unmet ? { position: null, problem: `Per ${action} devi trovarti ${placeWords(unmet)}.` } : { position };
}

export interface ComposerProps
{
  cards: DraftCard[];
  dispatch: Dispatch<DraftAction>;
  answering: Answering;
  conversation: Conversation;
  discussion: number;
  couple: Couple;
  me: Account;
  limits: Limits;
  /** The WRITE location constraints of the user: the position is checked again, and sent, with the message. */
  places: Constraint[];
  onClose: () => void;
}

/**
 * The writing area (CLAUDE.md, frontend point 6 and F8): the paragraphs as ordered cards, their links and self-citations,
 * a counter of the questions answered, the review before sending. Its text is unsent text, protected like any other.
 */
export function Composer({ cards, dispatch, answering, conversation, discussion, couple, me, limits, places, onClose }: ComposerProps)
{
  const { api } = useApp();
  const [reviewing, setReviewing] = useState(false);
  const [abandoning, setAbandoning] = useState(false);
  const [problem, setProblem] = useState<string>();
  const [previewed, setPreviewed] = useState<Previewed>();
  const release = useUnsentText(holdsText(cards));
  const graph = graphOf(conversation);
  const count = questionCount(cards, answering, conversation);
  const path = { params: { path: { id: conversation.id, number: discussion } } };

  const close = () =>
  {
    release();
    dispatch({ kind: 'clear' });
    onClose();
  };

  const preview = useCommand(() => ask(api.POST('/api/conversations/{id}/discussions/{number}/previews', { ...path, body: { paragraphs: toParagraphDrafts(cards) } })), {
    reread: false,
    onDone: setPreviewed,
  });
  const send = useCommand((position: Coordinates | null) => ask(api.POST('/api/conversations/{id}/discussions/{number}/messages', {
    ...path,
    body: { paragraphs: toParagraphDrafts(cards), localTime: localTime(new Date()), position },
  })), { onDone: close });

  const review = () =>
  {
    const found = draftProblem(cards);
    setProblem(found);

    if (!found)
    {
      setPreviewed(undefined);
      setReviewing(true);
      void preview.run();
    }
  };

  // The position, when writing needs it, is read again and checked again at the send, then sent with the message.
  const sendIt = async () =>
  {
    const { position, problem: unmet } = await positionFor(places, 'scrivere');

    if (unmet)
    {
      send.setError(unmet);
    }
    else
    {
      void send.run(position);
    }
  };

  return (
    <section aria-label="La tua risposta">
      {cards.map((card, index) => (
        <CardEditor key={card.key} card={card} index={index} last={index === cards.length - 1} dispatch={dispatch} answering={answering}
          conversation={conversation} discussion={discussion} graph={graph} me={me} limits={limits} />
      ))}
      {cards.length < MAX_CARDS ? (
        <div className="d-flex flex-wrap gap-2 mb-3">
          <Button variant="outline-primary" onClick={() => dispatch({ kind: 'add', type: 'SIMPLE', answering })}>
            Aggiungi un paragrafo
          </Button>
          <Button variant="outline-primary" onClick={() => dispatch({ kind: 'add', type: 'QUESTION', answering })}>
            Aggiungi una domanda
          </Button>
        </div>
      ) : (
        <p className="text-body-secondary">{MAX_REACHED}</p>
      )}
      {count.total > 0 && (
        <p className="small text-body-secondary">
          Domande a cui rispondi: {count.answered} di {count.total}
        </p>
      )}
      <div className="d-flex flex-wrap gap-2">
        <Button onClick={review}>Rivedi e invia</Button>
        <Button variant="outline-secondary" onClick={() => (holdsText(cards) ? setAbandoning(true) : close())}>
          Rinuncia alla risposta
        </Button>
      </div>
      <ErrorText error={problem} />
      {reviewing && (
        <Review cards={cards} conversation={conversation} couple={couple} me={me} previewed={previewed} error={preview.error ?? send.error}
          sending={send.pending} onBack={() => setReviewing(false)} onSend={() => void sendIt()} />
      )}
      <ConfirmDialog
        show={abandoning}
        text={ABANDON_TEXT}
        confirm="Rinuncia"
        cancel="Continua a scrivere"
        onConfirm={() =>
        {
          setAbandoning(false);
          close();
        }}
        onCancel={() => setAbandoning(false)}
      />
    </section>
  );
}

function titleOf(conversation: Conversation, id: string): string
{
  return findParagraph(conversation, id)?.title ?? 'un paragrafo';
}

interface CardEditorProps
{
  card: DraftCard;
  index: number;
  last: boolean;
  dispatch: Dispatch<DraftAction>;
  answering: Answering;
  conversation: Conversation;
  discussion: number;
  graph: ReturnType<typeof graphOf>;
  me: Account;
  limits: Limits;
}

function CardEditor({ card, index, last, dispatch, answering, conversation, discussion, graph, me, limits }: CardEditorProps)
{
  const label = `${PARAGRAPH_TYPE_LABELS[card.type]} ${index + 1}`;
  const edit = (field: 'title' | 'subtitle' | 'body') => (value: string) => dispatch({ kind: 'edit', key: card.key, field, value });
  const options = linkOptions(card, answering, conversation, discussion);
  const citations = citable(card, graph, me);
  const fixed = fixedLink(answering);
  const bodyLimit = card.type === 'SIMPLE' ? limits.simpleParagraphBody : card.type === 'QUESTION' ? limits.questionBody : limits.pointedAnswerBody;

  return (
    <Card className="mb-3" as="section" aria-label={label}>
      <Card.Header className="small fw-semibold">{label}</Card.Header>
      <Card.Body>
        {card.type === 'POINTED' ? (
          <p className="fw-semibold">In risposta a «{titleOf(conversation, card.question ?? '')}»</p>
        ) : (
          <TextField id={`${card.key}-title`} label="Titolo" value={card.title} onChange={edit('title')}
            maxLength={card.type === 'SIMPLE' ? limits.simpleParagraphTitle : limits.questionTitle} />
        )}
        {card.type === 'SIMPLE' && (
          <TextField id={`${card.key}-subtitle`} label="Sottotitolo (facoltativo)" value={card.subtitle} onChange={edit('subtitle')}
            maxLength={limits.simpleParagraphSubtitle} />
        )}
        <TextField id={`${card.key}-body`} label="Testo" value={card.body} onChange={edit('body')} maxLength={bodyLimit} rows={5} />
        {card.question && <div className="small text-body-secondary">Risponde a «{titleOf(conversation, card.question)}»</div>}
        {card.references.map((id) => (
          <div key={id} className="small text-body-secondary d-flex align-items-center gap-1">
            Risponde a «{titleOf(conversation, id)}»
            {!fixed && (
              <Button variant="link" size="sm" className="p-0" aria-label="Togli il collegamento"
                onClick={() => dispatch({ kind: 'unlink', key: card.key, from: id, topicOf: (references) => topicOf(graph, references) })}>
                ×
              </Button>
            )}
          </div>
        ))}
        {card.selfCitations.map((id) => (
          <div key={id} className="small text-body-secondary d-flex align-items-center gap-1">
            Richiama «{titleOf(conversation, id)}»
            <Button variant="link" size="sm" className="p-0" aria-label="Togli il collegamento" onClick={() => dispatch({ kind: 'uncite', key: card.key, paragraph: id })}>
              ×
            </Button>
          </div>
        ))}
        <div className="d-flex flex-wrap gap-2 mt-2">
          {options.length > 0 && (
            <Dropdown>
              <Dropdown.Toggle variant="outline-secondary" size="sm" id={`${card.key}-link`}>
                Collega a un paragrafo
              </Dropdown.Toggle>
              <Dropdown.Menu>
                {options.map((paragraph) => (
                  <Dropdown.Item key={paragraph.id} as="button" onClick={() => dispatch({ kind: 'link', key: card.key, to: paragraph.id })}>
                    {paragraph.title}
                  </Dropdown.Item>
                ))}
              </Dropdown.Menu>
            </Dropdown>
          )}
          {citations.length > 0 && (
            <Dropdown>
              <Dropdown.Toggle variant="outline-secondary" size="sm" id={`${card.key}-cite`}>
                Cita un tuo paragrafo
              </Dropdown.Toggle>
              <Dropdown.Menu>
                {citations.map((id) => (
                  <Dropdown.Item key={id} as="button" onClick={() => dispatch({ kind: 'cite', key: card.key, paragraph: id })}>
                    {titleOf(conversation, id)}
                  </Dropdown.Item>
                ))}
              </Dropdown.Menu>
            </Dropdown>
          )}
        </div>
      </Card.Body>
      <Card.Footer className="d-flex flex-wrap gap-2">
        <Button variant="outline-secondary" size="sm" disabled={index === 0} onClick={() => dispatch({ kind: 'move', key: card.key, by: -1 })}>
          Su
        </Button>
        <Button variant="outline-secondary" size="sm" disabled={last} onClick={() => dispatch({ kind: 'move', key: card.key, by: 1 })}>
          Giù
        </Button>
        <Button variant="outline-danger" size="sm" onClick={() => dispatch({ kind: 'remove', key: card.key })}>
          Rimuovi
        </Button>
      </Card.Footer>
    </Card>
  );
}

interface Previewed
{
  questionsLeftUnanswered: string[];
  blockingDiscussions: number[];
}

interface ReviewProps
{
  cards: DraftCard[];
  conversation: Conversation;
  couple: Couple;
  me: Account;
  previewed?: Previewed;
  error?: ReturnType<typeof useCommand>['error'];
  sending: boolean;
  onBack: () => void;
  onSend: () => void;
}

/**
 * The review before sending, always (frontend point 6): the message as the other partner will read it, and the server's
 * previews: a paragraph answered in another discussion stops the sending; questions left unanswered ask "Invia comunque".
 */
function Review({ cards, conversation, couple, me, previewed, error, sending, onBack, onSend }: ReviewProps)
{
  const other = nameIn(couple, couple.partnerOne.id === me.id ? couple.partnerTwo.id : couple.partnerOne.id);
  const blocking = (previewed?.blockingDiscussions ?? []).map((number) => conversation.discussions.find((candidate) => candidate.id === number));
  const skipped = previewed?.questionsLeftUnanswered ?? [];

  return (
    <Modal show onHide={onBack} size="lg" scrollable aria-labelledby="review-title">
      <Modal.Header closeButton closeLabel="Chiudi">
        <Modal.Title id="review-title" className="h5">
          Rivedi il messaggio
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <p className="text-body-secondary">Così lo leggerà {other}:</p>
        <Card className="mb-3">
          <Card.Body>
            {cards.map((card) => (
              <section key={card.key} className="mb-3">
                {card.type === 'QUESTION' && <div className="small text-uppercase text-body-secondary">{PARAGRAPH_TYPE_LABELS.QUESTION}</div>}
                <h3 className="h6 mb-1">{card.type === 'POINTED' ? `In risposta a «${titleOf(conversation, card.question ?? '')}»` : card.title}</h3>
                {card.type === 'SIMPLE' && card.subtitle.trim() !== '' && <UserText text={card.subtitle} className="text-body-secondary mb-1" />}
                <UserText text={card.body} className="mb-1" />
                {card.references.map((id) => (
                  <div key={id} className="small text-body-secondary">Risponde a «{titleOf(conversation, id)}»</div>
                ))}
                {card.selfCitations.map((id) => (
                  <div key={id} className="small text-body-secondary">Richiama «{titleOf(conversation, id)}»</div>
                ))}
              </section>
            ))}
          </Card.Body>
        </Card>
        {blocking.map((elsewhere, index) => (elsewhere?.originParagraphId ? (
          <p key={elsewhere.id} className="text-danger" role="alert">
            Al paragrafo «{titleOf(conversation, elsewhere.originParagraphId)}» si risponde in{' '}
            <Link to={`/conversazioni/${conversation.id}/discorsi/${elsewhere.id}`}>«{elsewhere.title ?? MAIN_DISCUSSION}»</Link>: scrivi lì.
          </p>
        ) : (
          <p key={`unknown-${index}`} className="text-danger" role="alert">
            {ANSWERED_ELSEWHERE}
          </p>
        )))}
        {skipped.length > 0 && (
          <p>
            Non rispondi a queste domande: {skipped.map((id) => `«${titleOf(conversation, id)}»`).join(', ')}. Puoi tornare a scrivere o inviare comunque.
          </p>
        )}
        <p className="mb-0">{IRREVERSIBLE}</p>
        <ErrorText error={error} />
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={onBack}>
          Torna a scrivere
        </Button>
        <Button onClick={onSend} disabled={!previewed || blocking.length > 0 || sending}>
          {skipped.length > 0 ? 'Invia comunque' : 'Invia'}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
