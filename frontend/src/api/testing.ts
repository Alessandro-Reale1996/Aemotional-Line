import type { Limits } from './client';

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

/** A fake server: answers each request with the JSON the handler gives, and remembers the requests. */
export function fakeServer(handler: (request: Request) => unknown)
{
  const requests: Request[] = [];

  return {
    requests,
    send: async (request: Request) =>
    {
      requests.push(request);

      return Response.json(handler(request));
    },
  };
}
