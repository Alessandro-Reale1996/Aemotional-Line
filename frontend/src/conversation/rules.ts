import type { Account, Constraint, Couple, Discussion } from '../api/client';

/*
 * The rules of the slow system that the interface applies itself (CLAUDE.md, frontend point 6 and F7), kept apart from
 * the components so that they can be tested alone. The server applies the same rules and has the last word; the app
 * uses them to offer only what is possible, and to say why not.
 */

// ---- Durations ------------------------------------------------------------------------------------------------------

const ISO_DURATION = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$/;

/** An ISO-8601 duration as the API sends it, normalized by Java ("PT720H", "PT1H30M", "PT3S"), in milliseconds. */
export function durationMs(iso: string): number
{
  const parts = ISO_DURATION.exec(iso);

  if (!parts || iso === 'P' || iso.endsWith('T'))
  {
    throw new Error(`Not an ISO-8601 duration the app reads: ${iso}`);
  }

  const [, days = '0', hours = '0', minutes = '0', seconds = '0'] = parts;

  return ((Number(days) * 24 + Number(hours)) * 60 + Number(minutes)) * 60_000 + Number(seconds) * 1000;
}

const UNITS = [
  { ms: 86_400_000, one: 'giorno', many: 'giorni' },
  { ms: 3_600_000, one: 'ora', many: 'ore' },
  { ms: 60_000, one: 'minuto', many: 'minuti' },
  { ms: 1000, one: 'secondo', many: 'secondi' },
];

/** A duration written out (F7 decision 5): "2 ore", "1 ora e 30 minuti", "30 giorni". */
export function formatDuration(milliseconds: number): string
{
  let rest = Math.round(milliseconds / 1000) * 1000;
  const parts: string[] = [];

  for (const unit of UNITS)
  {
    const count = Math.floor(rest / unit.ms);
    rest -= count * unit.ms;

    if (count > 0)
    {
      parts.push(`${count} ${count === 1 ? unit.one : unit.many}`);
    }
  }

  if (parts.length === 0)
  {
    return '0 secondi';
  }

  return parts.length === 1 ? parts[0] ?? '' : `${parts.slice(0, -1).join(', ')} e ${parts.at(-1) ?? ''}`;
}

// ---- Position -------------------------------------------------------------------------------------------------------

export interface Coordinates
{
  latitude: number;
  longitude: number;
}

/** The mean radius of the Earth the server uses too (CLAUDE.md, 5c). */
const EARTH_RADIUS_METRES = 6_371_008.8;

/** The distance between two points with the Haversine formula, as the server computes it. */
export function distanceMetres(from: Coordinates, to: Coordinates): number
{
  const radians = (degrees: number) => (degrees * Math.PI) / 180;
  const latitude = radians(to.latitude - from.latitude);
  const longitude = radians(to.longitude - from.longitude);
  const a = Math.sin(latitude / 2) ** 2 + Math.cos(radians(from.latitude)) * Math.cos(radians(to.latitude)) * Math.sin(longitude / 2) ** 2;

  return 2 * EARTH_RADIUS_METRES * Math.asin(Math.min(1, Math.sqrt(a)));
}

/** Whether a position satisfies a location constraint; the boundary counts, in both directions, as on the server. */
export function satisfiesLocation(position: Coordinates, constraint: Constraint): boolean
{
  const distance = distanceMetres(position, { latitude: constraint.latitude ?? 0, longitude: constraint.longitude ?? 0 });
  const radius = constraint.radiusMetres ?? 0;

  return constraint.direction === 'BEYOND' ? distance >= radius : distance <= radius;
}

/** "entro 500 metri dal luogo concordato": never the coordinates (F7 decision 5). */
export function placeWords(constraint: Constraint): string
{
  const radius = Math.round(constraint.radiusMetres ?? 0);
  const distance = radius >= 1000 && radius % 1000 === 0 ? `${radius / 1000} km` : `${radius} metri`;

  return constraint.direction === 'BEYOND' ? `a più di ${distance} dal luogo concordato` : `entro ${distance} dal luogo concordato`;
}

// ---- Time of day ----------------------------------------------------------------------------------------------------

/** The local time the API wants with an action: "HH:mm". */
export function localTime(now: Date): string
{
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
}

/** "18:00" from "18:00" or "18:00:00". */
export function shortTime(time: string): string
{
  return time.slice(0, 5);
}

/** A time constraint is satisfied from its time until midnight, the boundary included, as on the server. */
export function timeAllows(notBefore: string, now: Date): boolean
{
  return localTime(now) >= shortTime(notBefore);
}

// ---- Constraints of a person ----------------------------------------------------------------------------------------

/** The constraints in force on a person for an action: those of the action's scope, and those of both. */
export function constraintsFor(inForce: Constraint[], user: string, action: 'READ' | 'WRITE'): Constraint[]
{
  return inForce.filter((constraint) => constraint.user === user && (constraint.scope === action || constraint.scope === 'BOTH'));
}

/** What opening an envelope needs from this person now. */
export interface Opening
{
  /** Set when a time constraint doesn't let the message open yet: the time from which it will. */
  notBefore?: string;
  /** The location constraints the position must satisfy: asked at the click, checked here first. */
  places: Constraint[];
  /** The wait after the opening before a reply, when there is one. */
  replyDelay?: number;
}

export function openingFor(inForce: Constraint[], me: Account, now: Date): Opening
{
  const reading = constraintsFor(inForce, me.id, 'READ');
  const blocking = reading.filter((constraint) => constraint.type === 'TIME' && constraint.notBefore && !timeAllows(constraint.notBefore, now));
  const delays = constraintsFor(inForce, me.id, 'WRITE').filter((constraint) => constraint.type === 'REPLY_DELAY' && constraint.delay);

  return {
    notBefore: blocking.map((constraint) => shortTime(constraint.notBefore ?? '')).sort().at(-1),
    places: reading.filter((constraint) => constraint.type === 'LOCATION'),
    replyDelay: delays.length > 0 ? Math.max(...delays.map((constraint) => durationMs(constraint.delay ?? 'PT0S'))) : undefined,
  };
}

// ---- Can one write? -------------------------------------------------------------------------------------------------

/**
 * Where a discussion stands for the person looking at it (F7 decision 4), the first case that applies. A reply can be
 * written only when every WRITE constraint is satisfied (frontend point 6): the wait and the time are known here; the
 * position is checked when the writing starts (F8).
 */
export type WriteStatus =
  | { kind: 'ended' }
  | { kind: 'closed' }
  | { kind: 'therapist'; next: string | null }
  | { kind: 'mine'; next: string }
  | { kind: 'unopened'; sender: string }
  | { kind: 'waiting'; until: Date }
  | { kind: 'time'; from: string }
  | { kind: 'location'; constraint: Constraint }
  | { kind: 'can'; first: boolean };

function otherPartner(couple: Couple, partner: string): string
{
  return couple.partnerOne.id === partner ? couple.partnerTwo.id : couple.partnerOne.id;
}

export function writeStatus(discussion: Discussion, couple: Couple, me: Account, inForce: Constraint[], now: Date): WriteStatus
{
  const last = discussion.messages.at(-1);

  if (!couple.active)
  {
    return { kind: 'ended' };
  }

  if (discussion.closed)
  {
    return { kind: 'closed' };
  }

  if (me.id === couple.therapist.id)
  {
    return { kind: 'therapist', next: last ? otherPartner(couple, last.sender) : null };
  }

  if (last && last.sender === me.id)
  {
    return { kind: 'mine', next: otherPartner(couple, me.id) };
  }

  if (last && (last.closedEnvelope || !last.readAt))
  {
    return { kind: 'unopened', sender: last.sender };
  }

  const writing = constraintsFor(inForce, me.id, 'WRITE');

  // The wait counts from the opening of the message being answered: a first message answers nothing.
  if (last?.readAt)
  {
    const readAt = new Date(last.readAt).getTime();
    const ends = writing
      .filter((constraint) => constraint.type === 'REPLY_DELAY' && constraint.delay)
      .map((constraint) => readAt + durationMs(constraint.delay ?? 'PT0S'))
      .filter((end) => end > now.getTime());

    if (ends.length > 0)
    {
      return { kind: 'waiting', until: new Date(Math.max(...ends)) };
    }
  }

  const times = writing
    .filter((constraint) => constraint.type === 'TIME' && constraint.notBefore && !timeAllows(constraint.notBefore, now))
    .map((constraint) => shortTime(constraint.notBefore ?? ''))
    .sort();

  if (times.length > 0)
  {
    return { kind: 'time', from: times.at(-1) ?? '' };
  }

  const place = writing.find((constraint) => constraint.type === 'LOCATION');

  if (place)
  {
    return { kind: 'location', constraint: place };
  }

  return { kind: 'can', first: !last };
}

/** The next moment the status may change by itself (the end of a wait), for a local timer: never the server. */
export function nextChange(status: WriteStatus): Date | undefined
{
  return status.kind === 'waiting' ? status.until : undefined;
}
