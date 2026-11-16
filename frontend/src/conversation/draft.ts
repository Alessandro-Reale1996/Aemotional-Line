import type { components } from '../api/schema';
import type { Account, Conversation, Discussion, Paragraph } from '../api/client';

type ParagraphDraft = components['schemas']['ParagraphDraft'];
export type CardType = Paragraph['type'];

/** The most a message holds (R6 decision 2; GET /api/limits says the same). */
export const MAX_CARDS = 20;

/** One paragraph being written: a card of the writing area (CLAUDE.md, F8). */
export interface Card
{
  key: string;
  type: CardType;
  title: string;
  subtitle: string;
  body: string;
  /** The paragraphs of the message being answered this card answers (never a question: see `question`). */
  references: string[];
  selfCitations: string[];
  /** The question a pointed answer answers. */
  question?: string;
}

/**
 * What a draft can answer (CLAUDE.md, paragraph link rules 2-4 and 6): the paragraphs of the message being answered, or,
 * in the first message of a discussion born from a paragraph, only that paragraph, which every card must answer.
 */
export interface Answering
{
  paragraphs: Paragraph[];
  /** The paragraph the discussion expands, when this is its first message. */
  origin?: Paragraph;
}

export function answeringIn(discussion: Discussion, conversation: Conversation): Answering
{
  const last = discussion.messages.at(-1);

  if (last)
  {
    return { paragraphs: last.paragraphs };
  }

  const origin = discussion.originParagraphId ? findParagraph(conversation, discussion.originParagraphId) : undefined;

  return origin ? { paragraphs: [origin], origin } : { paragraphs: [] };
}

export function findParagraph(conversation: Conversation, id: string): Paragraph | undefined
{
  for (const discussion of conversation.discussions)
  {
    for (const message of discussion.messages)
    {
      const found = message.paragraphs.find((paragraph) => paragraph.id === id);

      if (found)
      {
        return found;
      }
    }
  }

  return undefined;
}

/** The discussion that expands a paragraph, other than the given one: there the paragraph is answered (rule 6, 4c). */
export function expandedElsewhere(conversation: Conversation, paragraph: string, here: number): Discussion | undefined
{
  return conversation.discussions.find((discussion) => discussion.originParagraphId === paragraph && discussion.id !== here);
}

// ---- The cards ------------------------------------------------------------------------------------------------------

let next = 0;

function newCard(type: CardType, answering: Answering, extra: Partial<Card> = {}): Card
{
  next += 1;

  return {
    key: `card-${next}`,
    type,
    title: '',
    subtitle: '',
    body: '',
    // In the first message of a discussion born from a paragraph every card answers it (rule 3).
    references: answering.origin && type !== 'POINTED' ? [answering.origin.id] : [],
    selfCitations: [],
    ...extra,
  };
}

export type DraftAction =
  | { kind: 'add'; type: 'SIMPLE' | 'QUESTION'; answering: Answering }
  | { kind: 'reply'; to: Paragraph; answering: Answering }
  | { kind: 'edit'; key: string; field: 'title' | 'subtitle' | 'body'; value: string }
  | { kind: 'move'; key: string; by: -1 | 1 }
  | { kind: 'remove'; key: string }
  | { kind: 'link'; key: string; to: string }
  | { kind: 'unlink'; key: string; from: string; topicOf: (references: string[]) => Set<string> }
  | { kind: 'cite'; key: string; paragraph: string }
  | { kind: 'uncite'; key: string; paragraph: string }
  | { kind: 'clear' };

/** Whether a question already has its pointed answer in the draft: a question gets one answer (rule 2). */
export function answeredInDraft(cards: Card[], question: string): boolean
{
  return cards.some((card) => card.question === question);
}

export function draftReducer(cards: Card[], action: DraftAction): Card[]
{
  const change = (key: string, update: (card: Card) => Card) => cards.map((card) => (card.key === key ? update(card) : card));

  switch (action.kind)
  {
    case 'add':
      return cards.length >= MAX_CARDS ? cards : [...cards, newCard(action.type, action.answering)];
    case 'reply':
      if (cards.length >= MAX_CARDS)
      {
        return cards;
      }

      if (action.to.type === 'QUESTION')
      {
        return answeredInDraft(cards, action.to.id) ? cards : [...cards, newCard('POINTED', action.answering, { question: action.to.id })];
      }

      return [...cards, newCard('SIMPLE', action.answering, { references: [action.to.id] })];
    case 'edit':
      return change(action.key, (card) => ({ ...card, [action.field]: action.value }));
    case 'move':
    {
      const from = cards.findIndex((card) => card.key === action.key);
      const to = from + action.by;

      if (from < 0 || to < 0 || to >= cards.length)
      {
        return cards;
      }

      const moved = [...cards];
      [moved[from], moved[to]] = [moved[to] as Card, moved[from] as Card];

      return moved;
    }
    case 'remove':
      return cards.filter((card) => card.key !== action.key);
    case 'link':
      return change(action.key, (card) => (card.references.includes(action.to) ? card : { ...card, references: [...card.references, action.to] }));
    case 'unlink':
      return change(action.key, (card) =>
      {
        const references = card.references.filter((id) => id !== action.from);
        const topic = action.topicOf(references);

        // A citation must stay in the card's topic; without links there is no topic, so no citation (rule 5).
        return { ...card, references, selfCitations: card.selfCitations.filter((id) => topic.has(id)) };
      });
    case 'cite':
      return change(action.key, (card) => (card.selfCitations.includes(action.paragraph) ? card : { ...card, selfCitations: [...card.selfCitations, action.paragraph] }));
    case 'uncite':
      return change(action.key, (card) => ({ ...card, selfCitations: card.selfCitations.filter((id) => id !== action.paragraph) }));
    case 'clear':
      return [];
  }
}

/** The paragraphs a card may still link (rule 6): of the message being answered, no question, none answered elsewhere. */
export function linkOptions(card: Card, answering: Answering, conversation: Conversation, here: number): Paragraph[]
{
  if (card.type === 'POINTED' || answering.origin)
  {
    return [];
  }

  return answering.paragraphs.filter((paragraph) =>
    paragraph.type !== 'QUESTION' && !card.references.includes(paragraph.id) && !expandedElsewhere(conversation, paragraph.id, here));
}

/** Whether a link of a card is fixed: the question of a pointed answer, the origin in a first message. */
export function fixedLink(answering: Answering): boolean
{
  return answering.origin !== undefined;
}

// ---- Topics, for the self-citations ---------------------------------------------------------------------------------

/** Who wrote each visible paragraph, and the answers joining them (both ways), across the whole conversation. */
export interface Graph
{
  senders: Map<string, string>;
  joined: Map<string, Set<string>>;
}

export function graphOf(conversation: Conversation): Graph
{
  const senders = new Map<string, string>();
  const joined = new Map<string, Set<string>>();
  const join = (a: string, b: string) =>
  {
    joined.set(a, (joined.get(a) ?? new Set()).add(b));
    joined.set(b, (joined.get(b) ?? new Set()).add(a));
  };

  for (const discussion of conversation.discussions)
  {
    for (const message of discussion.messages)
    {
      for (const paragraph of message.paragraphs)
      {
        senders.set(paragraph.id, message.sender);
        paragraph.references.forEach((reference) => join(paragraph.id, reference));
      }
    }
  }

  return { senders, joined };
}

/** The topic of a card: every paragraph joined through answers to the ones it answers (rule 5: the union of branches). */
export function topicOf(graph: Graph, answered: string[]): Set<string>
{
  const topic = new Set<string>();
  const waiting = [...answered];

  while (waiting.length > 0)
  {
    const current = waiting.pop() as string;

    if (!topic.has(current))
    {
      topic.add(current);
      graph.joined.get(current)?.forEach((other) => waiting.push(other));
    }
  }

  return topic;
}

/** What a card answers: its links, or the question of a pointed answer. */
export function answeredBy(card: Card): string[]
{
  return card.question ? [card.question, ...card.references] : card.references;
}

/** The user's own sent paragraphs a card may cite (rule 5): of its topic, not cited yet. None without links. */
export function citable(card: Card, graph: Graph, me: Account): string[]
{
  const answered = answeredBy(card);

  if (answered.length === 0)
  {
    return [];
  }

  return [...topicOf(graph, answered)].filter((id) => graph.senders.get(id) === me.id && !card.selfCitations.includes(id));
}

// ---- Counting and checking ------------------------------------------------------------------------------------------

/** "Domande a cui rispondi": the questions of the message being answered still to answer, and those the draft answers. */
export function questionCount(cards: Card[], answering: Answering, conversation: Conversation): { answered: number; total: number }
{
  // A question answered by a discussion born from it (with a message) doesn't wait for a pointed answer.
  const answeredByDiscussion = (id: string) =>
    conversation.discussions.some((discussion) => discussion.originParagraphId === id && discussion.messages.length > 0);
  const questions = answering.origin
    ? []
    : answering.paragraphs.filter((paragraph) => paragraph.type === 'QUESTION' && !answeredByDiscussion(paragraph.id));

  return { answered: questions.filter((question) => answeredInDraft(cards, question.id)).length, total: questions.length };
}

/** What stops the draft from going to the review, named by card (texts approved with F8). */
export function draftProblem(cards: Card[]): string | undefined
{
  if (cards.length === 0)
  {
    return 'Aggiungi almeno un paragrafo.';
  }

  for (const [index, card] of cards.entries())
  {
    if (card.type !== 'POINTED' && card.title.trim() === '')
    {
      return `Il paragrafo ${index + 1} non ha il titolo.`;
    }

    if (card.body.trim() === '')
    {
      return `Il paragrafo ${index + 1} non ha il testo.`;
    }
  }

  return undefined;
}

/** The cards as the API takes them: each type with its own fields only (R6: another field is refused). */
export function toParagraphDrafts(cards: Card[]): ParagraphDraft[]
{
  return cards.map((card) =>
  {
    switch (card.type)
    {
      case 'SIMPLE':
        return { type: 'SIMPLE', title: card.title, subtitle: card.subtitle.trim() === '' ? null : card.subtitle, body: card.body,
          references: card.references, selfCitations: card.selfCitations, question: null };
      case 'QUESTION':
        return { type: 'QUESTION', title: card.title, subtitle: null, body: card.body, references: card.references, selfCitations: card.selfCitations,
          question: null };
      case 'POINTED':
        return { type: 'POINTED', title: null, subtitle: null, body: card.body, references: [], selfCitations: card.selfCitations,
          question: card.question ?? null };
    }
  });
}

/** Whether the draft holds anything typed. */
export function holdsText(cards: Card[]): boolean
{
  return cards.some((card) => card.title !== '' || card.subtitle !== '' || card.body !== '');
}
