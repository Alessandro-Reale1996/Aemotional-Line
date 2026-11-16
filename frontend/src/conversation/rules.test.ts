import { describe, expect, it } from 'vitest';

import type { Discussion } from '../api/client';
import { activeCouple, constraint, message, paragraph, partner, patient, talkingCouple, therapist } from '../api/testing';
import {
  distanceMetres,
  durationMs,
  formatDuration,
  localTime,
  nextChange,
  openingFor,
  placeWords,
  satisfiesLocation,
  timeAllows,
  writeStatus,
} from './rules';

const HOUR = 3_600_000;
const milan = { latitude: 45.4642, longitude: 9.19 };
const rome = { latitude: 41.9028, longitude: 12.4964 };

function at(hours: number, minutes = 0): Date
{
  return new Date(2026, 10, 16, hours, minutes);
}

function discussion(messages: Discussion['messages'], extra: Partial<Discussion> = {}): Discussion
{
  return { id: 0, title: null, subtitle: null, closed: false, originParagraphId: null, lastSender: messages.at(-1)?.sender ?? null, messages, ...extra };
}

describe('durations', () =>
{
  it('read the ISO-8601 durations Java writes', () =>
  {
    expect(durationMs('PT720H')).toBe(30 * 24 * HOUR);
    expect(durationMs('PT1H30M')).toBe(1.5 * HOUR);
    expect(durationMs('PT3S')).toBe(3000);
    expect(durationMs('P1DT2H')).toBe(26 * HOUR);
    expect(() => durationMs('2 ore')).toThrow();
    expect(() => durationMs('PT')).toThrow();
  });

  it('are written out in Italian', () =>
  {
    expect(formatDuration(2 * HOUR)).toBe('2 ore');
    expect(formatDuration(1.5 * HOUR)).toBe('1 ora e 30 minuti');
    expect(formatDuration(durationMs('PT720H'))).toBe('30 giorni');
    expect(formatDuration(3000)).toBe('3 secondi');
    expect(formatDuration(26 * HOUR + 5 * 60_000)).toBe('1 giorno, 2 ore e 5 minuti');
    expect(formatDuration(60_000)).toBe('1 minuto');
  });
});

describe('positions', () =>
{
  it('are as far apart as the server computes (the cases of GeoPositionTest)', () =>
  {
    expect(distanceMetres(milan, milan)).toBeCloseTo(0, 9);
    expect(Math.abs(distanceMetres({ latitude: 0, longitude: 0 }, { latitude: 1, longitude: 0 }) - 111_195.08)).toBeLessThan(0.1);
    expect(distanceMetres(milan, rome)).toBeCloseTo(distanceMetres(rome, milan), 6);
    expect(Math.abs(distanceMetres(milan, rome) - 477_000)).toBeLessThan(2_000);
  });

  it('satisfy a constraint within or beyond its radius, the boundary included', () =>
  {
    const radius = distanceMetres(milan, rome);
    const within = constraint(patient.id, { type: 'LOCATION', latitude: milan.latitude, longitude: milan.longitude, radiusMetres: radius, direction: 'WITHIN' });
    const beyond = { ...within, direction: 'BEYOND' as const };

    expect(satisfiesLocation(rome, within)).toBe(true);
    expect(satisfiesLocation(rome, beyond)).toBe(true);
    expect(satisfiesLocation(milan, beyond)).toBe(false);
    expect(satisfiesLocation({ latitude: 38, longitude: 15 }, within)).toBe(false);
  });

  it('are said without coordinates', () =>
  {
    expect(placeWords(constraint(patient.id, { type: 'LOCATION', radiusMetres: 500, direction: 'WITHIN' }))).toBe('entro 500 metri dal luogo concordato');
    expect(placeWords(constraint(patient.id, { type: 'LOCATION', radiusMetres: 2000, direction: 'BEYOND' }))).toBe('a più di 2 km dal luogo concordato');
  });
});

describe('the time of day', () =>
{
  it('is sent as HH:mm and allows from its time until midnight', () =>
  {
    expect(localTime(at(9, 5))).toBe('09:05');
    expect(timeAllows('18:00', at(17, 59))).toBe(false);
    expect(timeAllows('18:00', at(18, 0))).toBe(true);
    expect(timeAllows('18:00:00', at(23, 59))).toBe(true);
  });
});

describe('opening an envelope', () =>
{
  it('waits for a reading time, asks for a place, announces the wait', () =>
  {
    const inForce = [
      constraint(patient.id, { type: 'TIME', scope: 'READ', notBefore: '18:00:00' }),
      constraint(patient.id, { type: 'TIME', scope: 'WRITE', notBefore: '21:00' }),
      constraint(patient.id, { type: 'LOCATION', scope: 'BOTH', radiusMetres: 500, direction: 'WITHIN', latitude: 45, longitude: 9 }),
      constraint(patient.id, { type: 'REPLY_DELAY', scope: 'WRITE', delay: 'PT2H' }),
      constraint(partner.id, { type: 'TIME', scope: 'READ', notBefore: '23:00' }),
    ];

    expect(openingFor(inForce, patient, at(17))).toMatchObject({ notBefore: '18:00', replyDelay: 2 * HOUR });
    expect(openingFor(inForce, patient, at(18)).notBefore).toBeUndefined();
    expect(openingFor(inForce, patient, at(18)).places).toHaveLength(1);
    expect(openingFor([], patient, at(18))).toEqual({ notBefore: undefined, places: [], replyDelay: undefined });
  });
});

describe('can one write?', () =>
{
  const fromSam = message(1, partner.id, [paragraph(1, 'Una domanda')], { readAt: new Date(at(16)).toISOString() });
  const mine = message(2, patient.id, [paragraph(2, 'La mia risposta')]);

  it('not in an ended couple, nor in a closed discussion', () =>
  {
    expect(writeStatus(discussion([]), { ...talkingCouple, active: false }, patient, [], at(12)).kind).toBe('ended');
    expect(writeStatus(discussion([], { closed: true }), talkingCouple, patient, [], at(12)).kind).toBe('closed');
  });

  it('never for the therapist, who sees whose turn it is', () =>
  {
    expect(writeStatus(discussion([]), talkingCouple, therapist, [], at(12))).toEqual({ kind: 'therapist', next: null });
    expect(writeStatus(discussion([mine]), talkingCouple, therapist, [], at(12))).toEqual({ kind: 'therapist', next: partner.id });
  });

  it('not twice in a row, nor before opening the last message', () =>
  {
    expect(writeStatus(discussion([mine]), talkingCouple, patient, [], at(12))).toEqual({ kind: 'mine', next: partner.id });
    expect(writeStatus(discussion([{ ...fromSam, closedEnvelope: true, readAt: null }]), talkingCouple, patient, [], at(12)))
      .toEqual({ kind: 'unopened', sender: partner.id });
  });

  it('not before the wait since the opening is over', () =>
  {
    const delay = [constraint(patient.id, { type: 'REPLY_DELAY', delay: 'PT2H' })];

    expect(writeStatus(discussion([fromSam]), talkingCouple, patient, delay, at(17))).toEqual({ kind: 'waiting', until: at(18) });
    expect(nextChange({ kind: 'waiting', until: at(18) })).toEqual(at(18));
    expect(writeStatus(discussion([fromSam]), talkingCouple, patient, delay, at(18)).kind).toBe('can');
  });

  it('a first message, whatever the wait: it answers nothing', () =>
  {
    const delay = [constraint(patient.id, { type: 'REPLY_DELAY', delay: 'PT2H' })];

    expect(writeStatus(discussion([]), talkingCouple, patient, delay, at(12))).toEqual({ kind: 'can', first: true });
  });

  it('not before the latest writing time, then only from the agreed place', () =>
  {
    const inForce = [
      constraint(patient.id, { type: 'TIME', notBefore: '18:00' }),
      constraint(patient.id, { type: 'TIME', scope: 'BOTH', notBefore: '19:30' }),
      constraint(patient.id, { type: 'TIME', scope: 'READ', notBefore: '23:00' }),
      constraint(patient.id, { type: 'LOCATION', scope: 'WRITE', radiusMetres: 500, direction: 'WITHIN', latitude: 45, longitude: 9 }),
    ];

    expect(writeStatus(discussion([fromSam]), talkingCouple, patient, inForce, at(17))).toEqual({ kind: 'time', from: '19:30' });
    expect(writeStatus(discussion([fromSam]), talkingCouple, patient, inForce, at(18))).toEqual({ kind: 'time', from: '19:30' });
    expect(writeStatus(discussion([fromSam]), talkingCouple, patient, inForce, at(20)).kind).toBe('location');
  });

  it('when nothing stands in the way, and the other partner constraints do not count', () =>
  {
    const theirs = [constraint(partner.id, { type: 'TIME', notBefore: '23:00' })];

    expect(writeStatus(discussion([fromSam]), activeCouple, patient, theirs, at(12))).toEqual({ kind: 'can', first: false });
  });
});
