import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import { Link } from 'react-router';

import { type Account, ask, type Constraint, type Conversation, type Couple, type Message, type Paragraph } from '../api/client';
import { useApp } from '../app/context';
import { ErrorText } from '../components/ErrorText';
import { UserText } from '../components/TextField';
import { useCommand } from '../components/useCommand';
import { nameIn } from '../negotiations/words';
import { formatDateTime } from '../texts/format';
import { PARAGRAPH_TYPE_LABELS } from '../texts/labels';
import { currentPosition, PositionError } from './position';
import { type Coordinates, formatDuration, localTime, openingFor, placeWords, satisfiesLocation } from './rules';

/** Where every visible paragraph of the conversation is, and its title: for the links between paragraphs. */
export type ParagraphIndex = Map<string, { discussion: number; title: string }>;

export function indexParagraphs(conversation: Conversation): ParagraphIndex
{
  const index: ParagraphIndex = new Map();

  for (const discussion of conversation.discussions)
  {
    for (const message of discussion.messages)
    {
      for (const paragraph of message.paragraphs)
      {
        index.set(paragraph.id, { discussion: discussion.id, title: paragraph.title });
      }
    }
  }

  return index;
}

/** The address of a paragraph: its discussion, and the paragraph's anchor in it. */
export function paragraphAddress(conversation: string, discussion: number, paragraph: string): string
{
  return `/conversazioni/${conversation}/discorsi/${discussion}#p-${paragraph}`;
}

export const POSITION_DENIED = 'Per aprirlo serve la tua posizione: consentila a questo sito nelle impostazioni del browser e riprova.';
export const POSITION_UNAVAILABLE = 'Non è stato possibile leggere la tua posizione. Riprova tra poco.';

interface LetterProps
{
  message: Message;
  conversation: Conversation;
  discussion: number;
  couple: Couple;
  me: Account;
  index: ParagraphIndex;
  inForce: Constraint[];
  now: Date;
  highlighted?: string;
}

/** A message as a letter (F7 decision 1), or a closed envelope when the user hasn't opened it yet (decision 3). */
export function Letter(props: LetterProps)
{
  return props.message.closedEnvelope ? <Envelope {...props} /> : <OpenLetter {...props} />;
}

function OpenLetter({ message, conversation, couple, me, index, highlighted }: LetterProps)
{
  const sender = nameIn(couple, message.sender);
  let opened: string | undefined;

  // No read receipts (R6): the sender never sees the opening; the recipient and the therapist do.
  if (message.readAt && message.sender !== me.id)
  {
    opened = me.id === couple.therapist.id
      ? `Aperto da ${nameIn(couple, partnerOtherThan(couple, message.sender))} il ${formatDateTime(message.readAt)}`
      : `L’hai aperto il ${formatDateTime(message.readAt)}`;
  }

  return (
    <Card as="article" className="mb-3" aria-label={`Messaggio di ${sender}`}>
      <Card.Header className="small d-flex flex-wrap justify-content-between gap-2">
        <span className="fw-semibold">{sender}</span>
        <span>{formatDateTime(message.sentAt)}</span>
      </Card.Header>
      <Card.Body>
        {message.paragraphs.map((paragraph) => (
          <ParagraphBlock key={paragraph.id} paragraph={paragraph} conversation={conversation.id} index={index}
            highlighted={paragraph.id === highlighted} />
        ))}
      </Card.Body>
      {opened && <Card.Footer className="small text-body-secondary">{opened}</Card.Footer>}
    </Card>
  );
}

function partnerOtherThan(couple: Couple, partner: string): string
{
  return couple.partnerOne.id === partner ? couple.partnerTwo.id : couple.partnerOne.id;
}

function ParagraphBlock({ paragraph, conversation, index, highlighted }: { paragraph: Paragraph; conversation: string; index: ParagraphIndex; highlighted: boolean })
{
  const link = (id: string, words: string) =>
  {
    const target = index.get(id);

    return (
      <div key={`${words}-${id}`} className="small text-body-secondary">
        {words}{' '}
        {target ? <Link to={paragraphAddress(conversation, target.discussion, id)}>«{target.title}»</Link> : 'un paragrafo della conversazione'}
      </div>
    );
  };

  return (
    <section id={`p-${paragraph.id}`} className={`mb-4 rounded ${highlighted ? 'paragraph-highlight' : ''}`.trim()} aria-label={paragraph.title}>
      {paragraph.type === 'QUESTION' && <div className="small text-uppercase text-body-secondary">{PARAGRAPH_TYPE_LABELS.QUESTION}</div>}
      <h3 className="h6 mb-1">{paragraph.title}</h3>
      {paragraph.subtitle && <UserText text={paragraph.subtitle} className="text-body-secondary mb-1" />}
      <UserText text={paragraph.body} className="mb-1" />
      {paragraph.references.map((id) => link(id, 'Risponde a'))}
      {paragraph.selfCitations.map((id) => link(id, 'Richiama'))}
    </section>
  );
}

/**
 * A message not opened yet (F7 decision 3): who sent it and when, nothing else. Opening it checks the user's READ
 * constraints: a time not reached yet hides "Apri"; a place is checked here first, so the position leaves the browser
 * only when it satisfies the constraint, with the opening, and is never stored.
 */
function Envelope({ message, conversation, discussion, couple, me, inForce, now }: LetterProps)
{
  const { api } = useApp();
  const opening = openingFor(inForce, me, now);
  const sender = nameIn(couple, message.sender);
  const open = useCommand((position: Coordinates | null) => ask(api.POST('/api/conversations/{id}/discussions/{number}/messages/{messageId}/open', {
    params: { path: { id: conversation.id, number: discussion, messageId: message.id } },
    body: { localTime: localTime(new Date()), position },
  })));

  const openIt = async () =>
  {
    let position: Coordinates | null = null;

    if (opening.places.length > 0)
    {
      try
      {
        position = await currentPosition();
      }
      catch (failure)
      {
        open.setError(failure instanceof PositionError && failure.reason === 'denied' ? POSITION_DENIED : POSITION_UNAVAILABLE);

        return;
      }

      const unmet = opening.places.find((constraint) => !satisfiesLocation(position as Coordinates, constraint));

      if (unmet)
      {
        open.setError(`Per aprirlo devi trovarti ${placeWords(unmet)}.`);

        return;
      }
    }

    void open.run(position);
  };

  return (
    <Card as="article" className="mb-3 bg-body-tertiary" aria-label={`Messaggio di ${sender}, da aprire`}>
      <Card.Body>
        <p className="mb-2">
          <span className="fw-semibold">Messaggio di {sender}</span> · {formatDateTime(message.sentAt)}
        </p>
        {opening.notBefore ? (
          <p className="mb-0">Potrai aprirlo dalle {opening.notBefore}.</p>
        ) : (
          <div className="d-flex flex-wrap align-items-center gap-2">
            <Button onClick={() => void openIt()} disabled={open.pending}>
              Apri
            </Button>
            {opening.replyDelay !== undefined && (
              <span className="small text-body-secondary">
                Da quando lo apri dovrai attendere {formatDuration(opening.replyDelay)} prima di rispondere.
              </span>
            )}
          </div>
        )}
        <ErrorText error={open.error} />
      </Card.Body>
    </Card>
  );
}
