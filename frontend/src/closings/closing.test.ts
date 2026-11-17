import { describe, expect, it } from 'vitest';

import type { ClosingRequest, TherapistRequest } from '../api/client';
import { openClosing, partner, patient, talkingCouple, therapist } from '../api/testing';
import { CLOSING_REQUEST_STATUS_LABELS, THERAPIST_REQUEST_STATUS_LABELS } from '../texts/labels';
import { closureLine, declarationOf, history, pendingRequest, pendingTherapistRequest, therapistOption } from './closing';

const LABELS = { request: CLOSING_REQUEST_STATUS_LABELS, therapist: THERAPIST_REQUEST_STATUS_LABELS };

function request(requester: string, status: ClosingRequest['status'], minute = 0): ClosingRequest
{
  const sentAt = `2026-11-17T09:${String(minute).padStart(2, '0')}:00Z`;

  return { requester, sentAt, status, endedAt: status === 'PENDING' ? null : `2026-11-17T09:${String(minute + 1).padStart(2, '0')}:00Z` };
}

function toTherapist(requester: string, status: TherapistRequest['status'], previous: ClosingRequest[], minute = 30): TherapistRequest
{
  return {
    requester,
    justification: 'Non riusciamo ad andare avanti.',
    sentAt: `2026-11-17T09:${minute}:00Z`,
    previousRequests: previous,
    status,
    decidedAt: status === 'PENDING' ? null : `2026-11-17T09:${minute + 1}:00Z`,
  };
}

describe('the pending requests', () =>
{
  it('are the last of each kind, while they wait', () =>
  {
    expect(pendingRequest(openClosing(1, { requests: [request(patient.id, 'REFUSED'), request(partner.id, 'PENDING', 5)] }))?.requester).toBe(partner.id);
    expect(pendingRequest(openClosing(1, { requests: [request(patient.id, 'REFUSED')] }))).toBeUndefined();
    expect(pendingTherapistRequest(openClosing(1, { therapistRequests: [toTherapist(patient.id, 'PENDING', [])] }))?.requester).toBe(patient.id);
    expect(pendingTherapistRequest(openClosing(1, { therapistRequests: [toTherapist(patient.id, 'REFUSED', [])] }))).toBeUndefined();
  });
});

describe('asking the therapist', () =>
{
  it('needs a failed attempt of one’s own: refused by the other, or withdrawn', () =>
  {
    expect(therapistOption(openClosing(1), patient.id)).toBe('none');
    expect(therapistOption(openClosing(1, { requests: [request(patient.id, 'PENDING')] }), patient.id)).toBe('none');
    expect(therapistOption(openClosing(1, { requests: [request(patient.id, 'REFUSED')] }), patient.id)).toBe('allowed');
    expect(therapistOption(openClosing(1, { requests: [request(patient.id, 'WITHDRAWN')] }), patient.id)).toBe('allowed');
    expect(therapistOption(openClosing(1, { requests: [request(partner.id, 'REFUSED')] }), patient.id), 'the other’s attempt is theirs').toBe('none');
  });

  it('needs a new failed attempt after a request to the therapist', () =>
  {
    const refused = request(patient.id, 'REFUSED');
    const asked = openClosing(1, { requests: [refused], therapistRequests: [toTherapist(patient.id, 'REFUSED', [refused])] });
    expect(therapistOption(asked, patient.id)).toBe('retry');

    const triedAgain = { ...asked, requests: [refused, request(partner.id, 'REFUSED', 40), request(patient.id, 'WITHDRAWN', 45)] };
    expect(therapistOption(triedAgain, patient.id)).toBe('allowed');
    // The other partner never asked the therapist: their own earlier failure still counts.
    expect(therapistOption(triedAgain, partner.id)).toBe('allowed');
  });
});

describe('how a discussion was closed', () =>
{
  it('by agreement, or by the therapist at someone’s request', () =>
  {
    const agreed = openClosing(1, { closed: true, way: 'AGREEMENT', closedAt: '2026-11-17T09:10:00Z' });
    expect(closureLine(agreed, talkingCouple, patient)).toBe('Il discorso è stato chiuso il 17 novembre 2026, 10:10 di comune accordo.');

    const refused = request(partner.id, 'REFUSED');
    const confirmed = openClosing(1, {
      closed: true,
      way: 'THERAPIST',
      closedAt: '2026-11-17T09:31:00Z',
      requests: [refused],
      therapistRequests: [toTherapist(patient.id, 'CONFIRMED', [refused])],
    });
    expect(closureLine(confirmed, talkingCouple, partner)).toBe('Il discorso è stato chiuso il 17 novembre 2026, 10:31 da Dott. Rossi, su richiesta di Alex.');
    expect(closureLine(confirmed, talkingCouple, patient)).toBe('Il discorso è stato chiuso il 17 novembre 2026, 10:31 da Dott. Rossi, su tua richiesta.');
    expect(closureLine(confirmed, talkingCouple, therapist)).toBe('Il discorso è stato chiuso il 17 novembre 2026, 10:31 da te, su richiesta di Alex.');
  });
});

describe('the history', () =>
{
  it('lists the requests that are over, both kinds, in the order they were sent', () =>
  {
    const refused = request(partner.id, 'REFUSED', 0);
    const withdrawn = request(patient.id, 'WITHDRAWN', 5);
    const closing = openClosing(1, {
      requests: [refused, withdrawn, request(partner.id, 'REFUSED', 40), request(patient.id, 'PENDING', 50)],
      therapistRequests: [toTherapist(patient.id, 'REFUSED', [refused, withdrawn], 30)],
    });

    expect(history(closing, talkingCouple, patient, LABELS).map((line) => line.text)).toEqual([
      'Richiesta di Sam · inviata il 17 novembre 2026, 10:00 · Rifiutata il 17 novembre 2026, 10:01',
      'La tua richiesta · inviata il 17 novembre 2026, 10:05 · Ritirata il 17 novembre 2026, 10:06',
      'La tua richiesta a Dott. Rossi · inviata il 17 novembre 2026, 10:30 · Non accolta il 17 novembre 2026, 10:31',
      'Richiesta di Sam · inviata il 17 novembre 2026, 10:40 · Rifiutata il 17 novembre 2026, 10:41',
    ]);
    expect(history(closing, talkingCouple, therapist, LABELS)[2]?.text).toMatch(/^Richiesta di Alex a te · /);
    expect(history(closing, talkingCouple, patient, LABELS)[2]?.justification).toBe('Non riusciamo ad andare avanti.');
  });
});

describe('a declaration', () =>
{
  it('is missing, hidden until both declared, or shown', () =>
  {
    const closing = openClosing(1, {
      closed: true,
      declarations: [
        { partner: patient.id, outcome: 'RESOLVED', reason: 'Ci siamo chiariti.', declaredAt: '2026-11-17T10:00:00Z' },
        { partner: partner.id, outcome: null, reason: null, declaredAt: '2026-11-17T10:05:00Z' },
      ],
    });

    expect(declarationOf(closing, patient.id).kind).toBe('shown');
    expect(declarationOf(closing, partner.id).kind).toBe('hidden');
    expect(declarationOf(openClosing(1, { closed: true }), partner.id).kind).toBe('missing');
  });
});
