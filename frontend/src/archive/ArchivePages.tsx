import { type FormEvent, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import { Link, NavLink, Outlet, useLoaderData, useNavigate, useRouteLoaderData } from 'react-router';

import {
  type Account,
  type ArchiveEntry,
  type ArchiveProposal,
  type ArchiveSearchResult,
  ask,
  type Conversation,
  type Couple,
  type NegotiationHistory,
  type Paragraph,
  type TopicHistory,
  type UnansweredQuestions,
} from '../api/client';
import { useAccount, useApp } from '../app/context';
import { ErrorText } from '../components/ErrorText';
import { useCommand } from '../components/useCommand';
import { MAIN_DISCUSSION, paragraphAddress } from '../conversation/Letters';
import { partnersOf } from '../couples/CoupleCard';
import { ProposalCard } from '../negotiations/NegotiationPage';
import { nameIn, who } from '../negotiations/words';
import { formatDate, formatDateTime } from '../texts/format';
import { ARCHIVE_SECTION_LABELS, FAILED_OUTCOME_LABELS, PARAGRAPH_TYPE_LABELS, PURPOSE_LABELS } from '../texts/labels';
import { byMessage, discussionName, highlight, periodProblem, rememberedSearch, rememberSearch, waitingQuestions } from './archive';

/** The data every page of a conversation's archive shares: the conversation (for the names of discussions and paragraphs) and the couple. */
export interface ArchiveData
{
  conversation: Conversation;
  couple: Couple;
}

export const ARCHIVE_ROUTE = 'archive';
export const NO_ARCHIVE = 'Non c’è ancora un archivio.';
export const UNOPENED_LEFT_OUT = 'I messaggi che non hai ancora aperto non compaiono nella ricerca.';
export const NOTHING_IN_PERIOD = 'Nessun messaggio in questo periodo.';
export const NONE = 'Nessuna.';

/** The topic history's rule, in words (texts approved with F10). */
export const RULE_TEXTS: Record<TopicHistory['rule'], string> = {
  MAIN_CONVERSATION: 'Comprende i paragrafi collegati a questo nella conversazione principale e tutti i discorsi specifici nati da loro.',
  SPECIFIC_DISCUSSION: 'Comprende i paragrafi collegati a questo, anche quelli della conversazione principale da cui nasce il discorso.',
};

function useArchive(): ArchiveData
{
  return useRouteLoaderData(ARCHIVE_ROUTE) as ArchiveData;
}

function isPartner(couple: Couple, me: Account): boolean
{
  return me.id === couple.partnerOne.id || me.id === couple.partnerTwo.id;
}

/** /archivio, the patient's bar link, when no couple of theirs has a conversation yet. */
export function NoArchivePage()
{
  return (
    <>
      <h1 className="h4 mb-3">Archivio</h1>
      <p>{NO_ARCHIVE}</p>
    </>
  );
}

/**
 * /conversazioni/:id/archivio (CLAUDE.md, F10): the header and the four tabs, each a page of its own with its own data;
 * the topic history opens below them too.
 */
export function ArchiveLayout()
{
  const { conversation, couple } = useArchive();
  const base = `/conversazioni/${conversation.id}/archivio`;
  const tabs = [
    { to: base, label: 'Cerca', end: true },
    { to: `${base}/periodo`, label: 'Per periodo' },
    { to: `${base}/domande`, label: 'Domande senza risposta' },
    { to: `${base}/stipule`, label: 'Stipule concluse' },
  ];

  return (
    <>
      <p className="mb-2">
        <Link to={`/conversazioni/${conversation.id}`}>Torna alla conversazione</Link>
      </p>
      <h1 className="h4 mb-1">Archivio</h1>
      <p className="text-body-secondary mb-1">{partnersOf(couple)}</p>
      <p>L’archivio raccoglie tutto ciò che è stato scritto e stipulato, e resta consultabile anche dopo la fine del percorso.</p>
      <nav aria-label="Archivio" className="mb-4">
        <ul className="nav nav-pills flex-wrap gap-1">
          {tabs.map((tab) => (
            <li key={tab.to} className="nav-item">
              <NavLink to={tab.to} end={tab.end} className="nav-link">
                {tab.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <Outlet />
    </>
  );
}

/** A text with the searched words marked; plain text, its line breaks kept (frontend point 8). */
function Highlighted({ text, searched, className = '', as: Tag = 'p' }: { text: string; searched?: string; className?: string; as?: 'p' | 'h3' })
{
  const parts = searched ? highlight(text, searched) : [{ text, found: false }];

  return (
    <Tag className={`preserve-lines ${className}`.trim()}>
      {parts.map((part, index) => (part.found ? <mark key={index}>{part.text}</mark> : <span key={index}>{part.text}</span>))}
    </Tag>
  );
}

interface EntriesProps
{
  entries: ArchiveEntry[];
  searched?: string;
  /** The paragraph a topic history starts from, highlighted. */
  chosen?: string;
}

/**
 * Archive lines, one card per message (F10): "Discorso · Tu · date", then its paragraphs with their links; a closed
 * envelope shows only who sent it, and when, with a link to open it in its discussion.
 */
function Entries({ entries, searched, chosen }: EntriesProps)
{
  const { conversation, couple } = useArchive();
  const me = useAccount();
  const paragraphs = new Map<string, Paragraph>(conversation.discussions.flatMap((discussion) =>
    discussion.messages.flatMap((message) => message.paragraphs.map((paragraph) => [paragraph.id, paragraph] as const))));
  const titleOf = (id: string) => paragraphs.get(id)?.title;

  return (
    <>
      {byMessage(entries).map((group) =>
      {
        const discussion = discussionName(conversation, group.discussionId);
        const sender = group.sender === me?.id ? 'Tu' : nameIn(couple, group.sender);

        if (group.closedEnvelope)
        {
          return (
            <Card key={group.messageId} as="article" className="mb-3 bg-body-tertiary" aria-label={`Messaggio di ${nameIn(couple, group.sender)}, da aprire`}>
              <Card.Body>
                <p className="mb-1">
                  {discussion} · Messaggio di {nameIn(couple, group.sender)} · {formatDateTime(group.sentAt)} · da aprire
                </p>
                <Link to={`/conversazioni/${conversation.id}/discorsi/${group.discussionId}`}>Aprilo nel discorso</Link>
              </Card.Body>
            </Card>
          );
        }

        return (
          <Card key={group.messageId} as="article" className="mb-3" aria-label={`Messaggio: ${discussion}, ${sender}, ${formatDateTime(group.sentAt)}`}>
            <Card.Header className="small">
              {discussion} · {sender} · {formatDateTime(group.sentAt)}
            </Card.Header>
            <Card.Body>
              {group.entries.map((entry) =>
              {
                const id = entry.paragraphId ?? '';
                const references = paragraphs.get(id)?.references ?? [];

                return (
                  <section key={id} className={`mb-3 rounded ${id === chosen ? 'paragraph-highlight' : ''}`.trim()} aria-label={entry.title ?? ''}
                    aria-current={id === chosen ? 'true' : undefined}>
                    {entry.type && entry.type !== 'SIMPLE' && (
                      <div className="small text-uppercase text-body-secondary">{PARAGRAPH_TYPE_LABELS[entry.type]}</div>
                    )}
                    <Highlighted as="h3" text={entry.title ?? ''} searched={searched} className="h6 mb-1" />
                    {entry.subtitle && <Highlighted text={entry.subtitle} searched={searched} className="text-body-secondary mb-1" />}
                    <Highlighted text={entry.body ?? ''} searched={searched} className="mb-1" />
                    {references.map((reference) => (
                      <div key={reference} className="small text-body-secondary">
                        Risponde a «{titleOf(reference) ?? 'un paragrafo della conversazione'}»
                      </div>
                    ))}
                    <div className="small d-flex flex-wrap gap-3">
                      <Link to={paragraphAddress(conversation.id, entry.discussionId, id)}>Vai al paragrafo</Link>
                      {id !== chosen && <Link to={`/conversazioni/${conversation.id}/archivio/argomenti/${id}`}>Storia dell’argomento</Link>}
                    </div>
                  </section>
                );
              })}
            </Card.Body>
          </Card>
        );
      })}
    </>
  );
}

/** The search tab: the words searched and what was found stay in memory only (F10 decision 2). */
export function SearchPage()
{
  const { api } = useApp();
  const { conversation, couple } = useArchive();
  const me = useAccount();
  const remembered = rememberedSearch(conversation.id);
  const [text, setText] = useState(remembered?.text ?? '');
  const [found, setFound] = useState<{ text: string; result: ArchiveSearchResult } | undefined>(remembered);
  const run = useCommand(async (words: string) =>
  {
    const result = await ask(api.POST('/api/conversations/{id}/archive/search', { params: { path: { id: conversation.id } }, body: { text: words } }));
    rememberSearch(conversation.id, words, result);
    setFound({ text: words, result });
  }, { reread: false });

  const onSubmit = (event: FormEvent) =>
  {
    event.preventDefault();

    if (text.trim() === '')
    {
      run.setError('Scrivi cosa cercare.');
    }
    else
    {
      void run.run(text);
    }
  };

  return (
    <section aria-labelledby="search-title">
      <h2 id="search-title" className="visually-hidden">Cerca</h2>
      <Form noValidate onSubmit={onSubmit} className="mb-3" role="search">
        <Form.Group className="mb-2" controlId="archive-search">
          <Form.Label>Cerca una parola o una frase</Form.Label>
          <Form.Control value={text} onChange={(event) => setText(event.target.value)} autoComplete="off" disabled={run.pending} />
        </Form.Group>
        <Button type="submit" disabled={run.pending}>
          Cerca
        </Button>
        <ErrorText error={run.error} />
      </Form>
      {me && isPartner(couple, me) && <p className="small text-body-secondary">{UNOPENED_LEFT_OUT}</p>}
      {found && <SearchResults searched={found.text} result={found.result} />}
    </section>
  );
}

function SearchResults({ searched, result }: { searched: string; result: ArchiveSearchResult })
{
  if (result.paragraphs.length === 0 && result.proposals.length === 0)
  {
    return <p>Nessun risultato per «{searched}».</p>;
  }

  return (
    <>
      <h3 className="h5">Nei messaggi ({result.paragraphs.length})</h3>
      <Entries entries={result.paragraphs} searched={searched} />
      <h3 className="h5">Nelle stipule ({result.proposals.length})</h3>
      {result.proposals.map((found) => <FoundProposal key={found.proposal.id} found={found} searched={searched} />)}
    </>
  );
}

function FoundProposal({ found, searched }: { found: ArchiveProposal; searched: string })
{
  const { couple } = useArchive();
  const me = useAccount();
  const { proposal } = found;
  const author = proposal.author === me?.id ? 'la tua proposta' : `proposta di ${nameIn(couple, proposal.author)}`;

  return (
    <Card className="mb-3">
      <Card.Header className="small">
        {PURPOSE_LABELS[found.purpose]} · {ARCHIVE_SECTION_LABELS[found.section]} · {author}
        {proposal.sentAt && ` del ${formatDate(proposal.sentAt)}`}
      </Card.Header>
      <Card.Body>
        {proposal.kind === 'AGREEMENT' ? (
          <Highlighted text={proposal.text ?? ''} searched={searched} className="mb-0" />
        ) : (
          <>
            <Highlighted text={proposal.title ?? ''} searched={searched} className="fw-semibold mb-1" />
            <Highlighted text={proposal.subtitle ?? ''} searched={searched} className="mb-0" />
          </>
        )}
      </Card.Body>
    </Card>
  );
}

export interface PeriodData
{
  dal: string;
  al: string;
  problem?: string;
  entries: ArchiveEntry[];
}

/** The period tab: the days in the address (F10 decision 1), the messages sent in them, closed envelopes in their place. */
export function PeriodPage()
{
  const { dal, al, problem, entries } = useLoaderData() as PeriodData;
  const navigate = useNavigate();
  const [from, setFrom] = useState(dal);
  const [to, setTo] = useState(al);
  const [error, setError] = useState<string>();

  const submit = (event: FormEvent) =>
  {
    event.preventDefault();
    const wrong = periodProblem(from, to);
    setError(wrong);

    if (!wrong)
    {
      void navigate({ search: `?dal=${from}&al=${to}` });
    }
  };

  return (
    <section aria-labelledby="period-title">
      <h2 id="period-title" className="visually-hidden">Per periodo</h2>
      <Form noValidate onSubmit={submit} className="mb-3">
        <Row className="align-items-end">
          <Form.Group as={Col} xs={6} sm={4} className="mb-2" controlId="period-from">
            <Form.Label>Dal</Form.Label>
            <Form.Control type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
          </Form.Group>
          <Form.Group as={Col} xs={6} sm={4} className="mb-2" controlId="period-to">
            <Form.Label>Al</Form.Label>
            <Form.Control type="date" value={to} onChange={(event) => setTo(event.target.value)} />
          </Form.Group>
          <Col xs={12} sm={4} className="mb-2">
            <Button type="submit">Mostra</Button>
          </Col>
        </Row>
        <ErrorText error={error ?? problem} />
      </Form>
      {!problem && (entries.length === 0 ? <p>{NOTHING_IN_PERIOD}</p> : <Entries entries={entries} />)}
    </section>
  );
}

/** The questions tab: those a reply skipped for good, and those still waiting for one. */
export function QuestionsPage()
{
  const questions = useLoaderData() as UnansweredQuestions;
  const waiting = waitingQuestions(questions);

  return (
    <>
      <section className="mb-4" aria-labelledby="skipped-title">
        <h2 id="skipped-title" className="h5 mb-1">Lasciate senza risposta</h2>
        <p className="small text-body-secondary">Domande a cui la risposta successiva non ha risposto.</p>
        {questions.leftUnanswered.length === 0 ? <p>{NONE}</p> : <Entries entries={questions.leftUnanswered} />}
      </section>
      <section className="mb-4" aria-labelledby="waiting-title">
        <h2 id="waiting-title" className="h5 mb-1">In attesa di risposta</h2>
        <p className="small text-body-secondary">Domande dell’ultimo messaggio a cui si deve ancora rispondere.</p>
        {waiting.length === 0 ? <p>{NONE}</p> : <Entries entries={waiting} />}
      </section>
    </>
  );
}

/** The concluded stipule: those that took effect, those that failed, each with its proposals behind a "Mostra". */
export function ConcludedNegotiationsPage()
{
  const history = useLoaderData() as NegotiationHistory;
  const { conversation, couple } = useArchive();
  const me = useAccount();

  if (!me)
  {
    return null;
  }

  const proposals = (list: NegotiationHistory['tookEffect'][number]['proposals']) => (
    <details>
      <summary>Mostra le proposte ({list.length})</summary>
      <div className="mt-2">
        {list.map((proposal, index) => (
          <ProposalCard key={proposal.id} proposal={proposal} couple={couple} me={me} latest={index === list.length - 1} />
        ))}
      </div>
    </details>
  );

  return (
    <>
      <section className="mb-4" aria-labelledby="took-effect-title">
        <h2 id="took-effect-title" className="h5">Con effetto</h2>
        {history.tookEffect.length === 0 && <p>{NONE}</p>}
        {history.tookEffect.map((negotiation) =>
        {
          const opened = negotiation.purpose === 'OPENED_DISCUSSION' && negotiation.discussionId !== null ? negotiation.discussionId : undefined;

          return (
            <Card key={negotiation.negotiationId} as="article" className="mb-3" aria-label={`${PURPOSE_LABELS[negotiation.purpose]} del ${formatDate(negotiation.archivedAt)}`}>
              <Card.Header className="small">
                {PURPOSE_LABELS[negotiation.purpose]} · {formatDate(negotiation.archivedAt)}
                {opened !== undefined && (
                  <>
                    {' · '}
                    <Link to={`/conversazioni/${conversation.id}/discorsi/${opened}`}>«{discussionName(conversation, opened)}»</Link>
                  </>
                )}
              </Card.Header>
              <Card.Body>{proposals(negotiation.proposals)}</Card.Body>
            </Card>
          );
        })}
      </section>
      <section className="mb-4" aria-labelledby="failed-title">
        <h2 id="failed-title" className="h5">Non riuscite</h2>
        {history.failed.length === 0 && <p>{NONE}</p>}
        {history.failed.map((negotiation) => (
          <Card key={negotiation.negotiationId} as="article" className="mb-3" aria-label={`${PURPOSE_LABELS[negotiation.attemptedPurpose]} non riuscita`}>
            <Card.Header className="small">
              {PURPOSE_LABELS[negotiation.attemptedPurpose]} · {FAILED_OUTCOME_LABELS[negotiation.outcome]} da {who(couple, negotiation.closedBy, me)} il{' '}
              {formatDate(negotiation.closedAt)} · iniziata il {formatDate(negotiation.startedAt)}
            </Card.Header>
            <Card.Body>{proposals(negotiation.proposals)}</Card.Body>
          </Card>
        ))}
      </section>
    </>
  );
}

/** The history by topic of a paragraph (spec 1.3.4): its rule in words, the paragraphs in sending order, the chosen one highlighted. */
export function TopicPage()
{
  const topic = useLoaderData() as TopicHistory;
  const chosen = topic.entries.find((entry) => entry.paragraphId === topic.paragraphId);

  return (
    <section aria-labelledby="topic-title">
      <h2 id="topic-title" className="h5 mb-1">Storia dell’argomento</h2>
      <p className="mb-1">Parte da «{chosen?.title ?? MAIN_DISCUSSION}»</p>
      <p className="small text-body-secondary">{RULE_TEXTS[topic.rule]}</p>
      <Entries entries={topic.entries} chosen={topic.paragraphId} />
    </section>
  );
}
