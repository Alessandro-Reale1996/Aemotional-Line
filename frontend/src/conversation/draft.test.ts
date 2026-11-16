import { describe, expect, it } from 'vitest';

import type { Conversation } from '../api/client';
import { conversationWith, message, paragraph, partner, patient } from '../api/testing';
import {
  type Answering,
  type Card,
  citable,
  draftProblem,
  draftReducer,
  graphOf,
  linkOptions,
  MAX_CARDS,
  questionCount,
  toParagraphDrafts,
  topicOf,
} from './draft';

const mine = paragraph(1, 'Il mio primo pensiero');
const theirs = paragraph(2, 'La sua risposta', { references: [mine.id] });
const question = paragraph(3, 'Mi spieghi?', { type: 'QUESTION' });
const another = paragraph(4, 'Un altro punto');
const conversation = conversationWith([message(1, patient.id, [mine]), message(2, partner.id, [theirs, question, another])]);
const answering: Answering = { paragraphs: [theirs, question, another] };

function play(...actions: Parameters<typeof draftReducer>[1][]): Card[]
{
  return actions.reduce(draftReducer, [] as Card[]);
}

describe('the cards', () =>
{
  it('are added, moved and removed, at most twenty', () =>
  {
    const cards = play({ kind: 'add', type: 'SIMPLE', answering }, { kind: 'add', type: 'QUESTION', answering });
    const [first, second] = cards as [Card, Card];

    expect(cards.map((card) => card.type)).toEqual(['SIMPLE', 'QUESTION']);
    expect(draftReducer(cards, { kind: 'move', key: second.key, by: -1 }).map((card) => card.key)).toEqual([second.key, first.key]);
    expect(draftReducer(cards, { kind: 'move', key: first.key, by: -1 })).toBe(cards);
    expect(draftReducer(cards, { kind: 'remove', key: first.key })).toEqual([second]);

    const full = Array.from({ length: MAX_CARDS + 3 }).reduce<Card[]>((all) => draftReducer(all, { kind: 'add', type: 'SIMPLE', answering }), []);
    expect(full).toHaveLength(MAX_CARDS);
  });

  it('answer a question with one pointed answer only, anything else with a linked paragraph', () =>
  {
    const cards = play({ kind: 'reply', to: question, answering }, { kind: 'reply', to: question, answering }, { kind: 'reply', to: theirs, answering });

    expect(cards).toHaveLength(2);
    expect(cards[0]).toMatchObject({ type: 'POINTED', question: question.id, references: [] });
    expect(cards[1]).toMatchObject({ type: 'SIMPLE', references: [theirs.id] });
  });

  it('answer the paragraph a discussion is born from, every one of them, in its first message', () =>
  {
    const born: Answering = { paragraphs: [another], origin: another };
    const cards = play({ kind: 'add', type: 'SIMPLE', answering: born }, { kind: 'add', type: 'QUESTION', answering: born });

    expect(cards.map((card) => card.references)).toEqual([[another.id], [another.id]]);
    expect(linkOptions(cards[0] as Card, born, conversation, 1)).toEqual([]);
  });

  it('may link the paragraphs of the message answered, no question, none answered elsewhere', () =>
  {
    const card = play({ kind: 'reply', to: theirs, answering })[0] as Card;
    const elsewhere: Conversation = {
      ...conversation,
      discussions: [...conversation.discussions, { id: 1, title: 'Altrove', subtitle: 'Sì', closed: false, originParagraphId: another.id, lastSender: null, messages: [] }],
    };

    expect(linkOptions(card, answering, conversation, 0).map((option) => option.id)).toEqual([another.id]);
    expect(linkOptions(card, answering, elsewhere, 0)).toEqual([]);
    expect(linkOptions(play({ kind: 'reply', to: question, answering })[0] as Card, answering, conversation, 0)).toEqual([]);
  });
});

describe('the self-citations', () =>
{
  const graph = graphOf(conversation);

  it('offer the user own paragraphs of the topic, none without a link', () =>
  {
    const card = play({ kind: 'reply', to: theirs, answering })[0] as Card;

    expect(topicOf(graph, [theirs.id])).toEqual(new Set([theirs.id, mine.id]));
    expect(citable(card, graph, patient)).toEqual([mine.id]);
    expect(citable(card, graph, partner), 'only one own paragraphs').toEqual([theirs.id]);
    expect(citable(play({ kind: 'add', type: 'SIMPLE', answering })[0] as Card, graph, patient)).toEqual([]);
    expect(citable(play({ kind: 'reply', to: another, answering })[0] as Card, graph, patient), 'another topic').toEqual([]);
  });

  it('go when the link that put them in the topic goes', () =>
  {
    const replied = play({ kind: 'reply', to: theirs, answering });
    const key = (replied[0] as Card).key;
    const cited = draftReducer(replied, { kind: 'cite', key, paragraph: mine.id });
    expect((cited[0] as Card).selfCitations).toEqual([mine.id]);

    const unlinked = draftReducer(cited, { kind: 'unlink', key, from: theirs.id, topicOf: (references) => topicOf(graph, references) });

    expect((unlinked[0] as Card).selfCitations).toEqual([]);
  });
});

describe('the draft', () =>
{
  it('counts the questions it answers, not those a discussion already answered', () =>
  {
    const second = paragraph(5, 'E poi?', { type: 'QUESTION' });
    const twoQuestions: Answering = { paragraphs: [question, second] };
    const cards = play({ kind: 'reply', to: question, answering: twoQuestions });
    const answeredElsewhere: Conversation = {
      ...conversation,
      discussions: [...conversation.discussions, {
        id: 1, title: 'E poi', subtitle: 'Sì', closed: false, originParagraphId: second.id, lastSender: patient.id, messages: [message(9, patient.id, [])],
      }],
    };

    expect(questionCount(cards, twoQuestions, conversation)).toEqual({ answered: 1, total: 2 });
    expect(questionCount(cards, twoQuestions, answeredElsewhere)).toEqual({ answered: 1, total: 1 });
  });

  it('names the card that misses a field', () =>
  {
    expect(draftProblem([])).toBe('Aggiungi almeno un paragrafo.');
    const cards = play({ kind: 'reply', to: question, answering }, { kind: 'add', type: 'SIMPLE', answering });
    const [pointed, simple] = cards as [Card, Card];

    expect(draftProblem(cards)).toBe('Il paragrafo 1 non ha il testo.');
    const answered = draftReducer(cards, { kind: 'edit', key: pointed.key, field: 'body', value: 'Ecco.' });
    expect(draftProblem(answered), 'a pointed answer has no title to write').toBe('Il paragrafo 2 non ha il titolo.');
    expect(draftProblem(draftReducer(answered, { kind: 'edit', key: simple.key, field: 'title', value: '   ' }))).toBe('Il paragrafo 2 non ha il titolo.');
  });

  it('goes to the API with each type own fields only', () =>
  {
    const cards = play({ kind: 'reply', to: question, answering }, { kind: 'add', type: 'SIMPLE', answering }, { kind: 'add', type: 'QUESTION', answering });
    const filled = cards.map((card) => ({ ...card, title: card.type === 'POINTED' ? '' : 'Titolo', subtitle: '', body: 'Testo' }));

    expect(toParagraphDrafts(filled)).toEqual([
      { type: 'POINTED', title: null, subtitle: null, body: 'Testo', references: [], selfCitations: [], question: question.id },
      { type: 'SIMPLE', title: 'Titolo', subtitle: null, body: 'Testo', references: [], selfCitations: [], question: null },
      { type: 'QUESTION', title: 'Titolo', subtitle: null, body: 'Testo', references: [], selfCitations: [], question: null },
    ]);
    expect(toParagraphDrafts([{ ...(filled[1] as Card), subtitle: 'Un sottotitolo' }])[0]?.subtitle).toBe('Un sottotitolo');
  });
});
