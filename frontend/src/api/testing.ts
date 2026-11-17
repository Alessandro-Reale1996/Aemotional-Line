import type { Account, Closing, Constraint, Conversation, Couple, Invitation, Limits, Message, Summary, Therapist } from './client';
import type { ErrorCode } from './errors';

/**
 * Data for the tests, typed by the spec (CLAUDE.md, frontend point 9): when the API changes, these stop compiling instead
 * of going on describing an API that no longer exists.
 */
export const limits: Limits = {
  textUnit: 'UTF-16 code units',
  simpleParagraphTitle: 100,
  simpleParagraphSubtitle: 200,
  simpleParagraphBody: 800,
  questionTitle: 250,
  questionBody: 350,
  pointedAnswerBody: 500,
  messageParagraphs: 20,
  discussionTitle: 100,
  discussionSubtitle: 200,
  agreementText: 4000,
  proposalJustification: 800,
  outcomeReason: 200,
  therapistRequestJustification: 800,
  replyDelayMaxDays: 30,
  displayName: 100,
  email: 254,
  passwordUnit: 'Unicode code points',
  passwordMinLength: 12,
  passwordMaxLength: 64,
  passwordMaxBytes: 72,
};

export const therapist: Account = {
  id: '0190a7e2-0000-7000-8000-000000000001',
  email: 'terapia@example.test',
  displayName: 'Dott. Rossi',
  role: 'THERAPIST',
};

export const patient: Account = {
  id: '0190a7e2-0000-7000-8000-000000000002',
  email: 'paziente@example.test',
  displayName: 'Alex',
  role: 'PATIENT',
};

export const partner: Account = {
  id: '0190a7e2-0000-7000-8000-000000000003',
  email: 'partner@example.test',
  displayName: 'Sam',
  role: 'PATIENT',
};

export const administrator: Account = {
  id: '0190a7e2-0000-7000-8000-000000000004',
  email: 'amministrazione@example.test',
  displayName: 'Amministrazione',
  role: 'ADMINISTRATOR',
};

const people = {
  partnerOne: { id: patient.id, displayName: patient.displayName },
  partnerTwo: { id: partner.id, displayName: partner.displayName },
  therapist: { id: therapist.id, displayName: therapist.displayName },
};

export const activeCouple: Couple = {
  id: '0190a7e2-0000-7000-8000-0000000000c1',
  ...people,
  active: true,
  endedAt: null,
  conversation: null,
};

/** An ended couple whose second partner deleted the account. */
export const endedCouple: Couple = {
  id: '0190a7e2-0000-7000-8000-0000000000c2',
  ...people,
  partnerTwo: { id: partner.id, displayName: null },
  active: false,
  endedAt: '2026-11-13T10:00:00Z',
  conversation: null,
};

export const CONVERSATION = '0190a7e2-0000-7000-8000-0000000000d1';

/** The couple once its conversation has started. */
export const talkingCouple: Couple = { ...activeCouple, conversation: CONVERSATION };

/** A message as the API gives it; `closedEnvelope` empties it, as for a partner who hasn't opened it. */
export function message(id: number, sender: string, paragraphs: Message['paragraphs'], extra: Partial<Message> = {}): Message
{
  return {
    id: `0190a7e2-0000-7000-8000-${String(id).padStart(12, '0')}`,
    sender,
    sentAt: '2026-11-16T17:30:00Z',
    readAt: null,
    closedEnvelope: false,
    questionsLeftUnanswered: [],
    paragraphs,
    ...extra,
  };
}

/** A paragraph as the API gives it. */
export function paragraph(id: number, title: string, extra: Partial<Message['paragraphs'][number]> = {}): Message['paragraphs'][number]
{
  return {
    id: `0190a7e2-0000-7000-8000-1${String(id).padStart(11, '0')}`,
    type: 'SIMPLE',
    title,
    subtitle: null,
    body: `Il testo di «${title}».`,
    references: [],
    selfCitations: [],
    ...extra,
  };
}

/** A conversation with only its main discussion, holding the messages given. */
export function conversationWith(messages: Message[], extra: Partial<Conversation> = {}): Conversation
{
  return {
    id: CONVERSATION,
    coupleId: activeCouple.id,
    agreement: 'Ci scriviamo con calma.',
    discussions: [{ id: 0, title: null, subtitle: null, closed: false, originParagraphId: null, lastSender: messages.at(-1)?.sender ?? null, messages }],
    ...extra,
  };
}

/** A constraint in force on a person. */
export function constraint(user: string, extra: Partial<Constraint>): Constraint
{
  return {
    user,
    type: 'TIME',
    scope: 'WRITE',
    description: 'For developers.',
    notBefore: null,
    delay: null,
    latitude: null,
    longitude: null,
    radiusMetres: null,
    direction: null,
    ...extra,
  };
}

/** The closing of a specific discussion nobody has asked to close yet. */
export function openClosing(discussion: number, extra: Partial<Closing> = {}): Closing
{
  return { discussionId: discussion, closed: false, way: null, closedAt: null, requests: [], therapistRequests: [], declarations: [], ...extra };
}

export const invitation: Invitation = {
  id: '0190a7e2-0000-7000-8000-0000000000a1',
  ...people,
  status: 'PENDING',
  acceptedBy: [],
  coupleId: null,
  createdAt: '2026-11-13T10:00:00Z',
  expiresAt: '2026-11-20T10:00:00Z',
};

export const emptySummary: Summary = { pendingInvitations: [], couples: [] };

export const therapists: Therapist[] = [
  { id: '0190a7e2-0000-7000-8000-0000000000e1', email: 'verdi@example.test', displayName: 'Dott.ssa Verdi', status: 'PENDING' },
  { id: '0190a7e2-0000-7000-8000-0000000000e2', email: 'neri@example.test', displayName: 'Dott. Neri', status: 'ACTIVE' },
  { id: '0190a7e2-0000-7000-8000-0000000000e3', email: 'bianchi@example.test', displayName: 'Dott.ssa Bianchi', status: 'DISABLED' },
];

/** An error answer as the API gives it: Problem Details with a code. */
export function problem(status: number, code: ErrorCode, extra: Record<string, unknown> = {}): Response
{
  return new Response(JSON.stringify({ title: 'Problem', status, detail: 'For developers.', instance: '/api', code, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

export function noContent(): Response
{
  return new Response(null, { status: 204 });
}

/**
 * A fake server: answers each request with what the handler gives (a Response as it is, anything else as JSON with 200),
 * and remembers the requests.
 */
export function fakeServer(handler: (request: Request) => unknown)
{
  const requests: Request[] = [];

  return {
    requests,
    paths: () => requests.map((request) => `${request.method} ${new URL(request.url).pathname}`),
    send: async (request: Request) =>
    {
      requests.push(request.clone());
      const answer = await handler(request);

      return answer instanceof Response ? answer : Response.json(answer);
    },
  };
}
