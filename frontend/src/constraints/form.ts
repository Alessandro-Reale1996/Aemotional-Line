import type { Constraint, ConstraintSpec, Couple } from '../api/client';
import { durationMs, shortTime } from '../conversation/rules';

/*
 * The therapist's proposal (CLAUDE.md, F9): the whole new set of constraints, one card each, starting from those in force.
 * The fields keep what was typed; they become a ConstraintSpec only once checked, when the proposal is reviewed.
 */

export type ConstraintType = Constraint['type'];
export type Scope = Constraint['scope'];
export type Direction = NonNullable<Constraint['direction']>;
export type DelayUnit = 'MINUTES' | 'HOURS' | 'DAYS';

export const DELAY_UNITS: Record<DelayUnit, { label: string; ms: number; iso: (amount: number) => string }> = {
  MINUTES: { label: 'minuti', ms: 60_000, iso: (amount) => `PT${amount}M` },
  HOURS: { label: 'ore', ms: 3_600_000, iso: (amount) => `PT${amount}H` },
  DAYS: { label: 'giorni', ms: 86_400_000, iso: (amount) => `P${amount}D` },
};

export interface ConstraintCard
{
  key: number;
  assignee: string;
  type: ConstraintType;
  /** The scope of a time constraint: writing by default, as on the server. */
  timeScope: Scope;
  notBefore: string;
  delayAmount: string;
  delayUnit: DelayUnit;
  /** A delay copied from those in force, sent as it was while its fields are untouched (it may not be whole minutes). */
  delayAsItWas?: string;
  latitude: string;
  longitude: string;
  radius: string;
  direction: Direction;
  /** The scope of a location constraint: reading by default, as on the server. */
  placeScope: Scope;
}

let nextKey = 1;

/** A new card: a time constraint for the first partner, every field empty. */
export function newCard(couple: Couple): ConstraintCard
{
  return {
    key: nextKey++,
    assignee: couple.partnerOne.id,
    type: 'TIME',
    timeScope: 'WRITE',
    notBefore: '',
    delayAmount: '',
    delayUnit: 'HOURS',
    latitude: '',
    longitude: '',
    radius: '',
    direction: 'WITHIN',
    placeScope: 'READ',
  };
}

/** A decimal as Italians may type it too: "45,4642". */
function decimalText(value: number): string
{
  return String(value).replace('.', ',');
}

/** The card of a constraint in force, so that the proposal starts from what applies now. */
export function cardOf(constraint: Constraint, couple: Couple): ConstraintCard
{
  const card = { ...newCard(couple), assignee: constraint.user, type: constraint.type };

  switch (constraint.type)
  {
    case 'TIME':
      return { ...card, timeScope: constraint.scope, notBefore: shortTime(constraint.notBefore ?? '') };
    case 'REPLY_DELAY':
    {
      const ms = durationMs(constraint.delay ?? 'PT0S');
      const unit = (['DAYS', 'HOURS', 'MINUTES'] as const).find((candidate) => ms % DELAY_UNITS[candidate].ms === 0) ?? 'MINUTES';

      return { ...card, delayAmount: decimalText(ms / DELAY_UNITS[unit].ms), delayUnit: unit, delayAsItWas: constraint.delay ?? undefined };
    }
    case 'LOCATION':
      return {
        ...card,
        latitude: constraint.latitude === null ? '' : decimalText(constraint.latitude),
        longitude: constraint.longitude === null ? '' : decimalText(constraint.longitude),
        radius: constraint.radiusMetres === null ? '' : decimalText(constraint.radiusMetres),
        direction: constraint.direction ?? 'WITHIN',
        placeScope: constraint.scope,
      };
  }
}

export type CardAction =
  | { kind: 'add'; couple: Couple }
  | { kind: 'edit'; key: number; change: Partial<Omit<ConstraintCard, 'key'>> }
  | { kind: 'remove'; key: number };

export function cardsReducer(cards: ConstraintCard[], action: CardAction): ConstraintCard[]
{
  switch (action.kind)
  {
    case 'add':
      return [...cards, newCard(action.couple)];
    case 'edit':
      return cards.map((card) =>
      {
        if (card.key !== action.key)
        {
          return card;
        }

        // A delay typed again is sent as typed.
        const touchesDelay = 'delayAmount' in action.change || 'delayUnit' in action.change;

        return { ...card, ...action.change, delayAsItWas: touchesDelay ? undefined : card.delayAsItWas };
      });
    case 'remove':
      return cards.filter((card) => card.key !== action.key);
  }
}

/** A decimal typed with a point or a comma, or undefined. */
export function parseDecimal(text: string): number | undefined
{
  const normalized = text.trim().replace(',', '.');

  return /^-?\d+(\.\d+)?$/.test(normalized) ? Number(normalized) : undefined;
}

/** What is wrong with a card, naming it (texts approved with F9); undefined when it can be proposed. */
export function cardProblem(card: ConstraintCard, number: number, maxDelayDays: number): string | undefined
{
  const problem = (text: string) => `Vincolo ${number}: ${text}`;

  switch (card.type)
  {
    case 'TIME':
      return card.notBefore === '' ? problem('manca l’orario.') : undefined;
    case 'REPLY_DELAY':
    {
      if (card.delayAsItWas)
      {
        return undefined;
      }

      if (card.delayAmount.trim() === '')
      {
        return problem('manca la durata.');
      }

      const amount = parseDecimal(card.delayAmount);
      const ms = amount === undefined ? NaN : amount * DELAY_UNITS[card.delayUnit].ms;

      return amount === undefined || !Number.isInteger(amount) || ms < 60_000 || ms > maxDelayDays * DELAY_UNITS.DAYS.ms
        ? problem(`la durata va da 1 minuto a ${maxDelayDays} giorni.`)
        : undefined;
    }
    case 'LOCATION':
    {
      const latitude = parseDecimal(card.latitude);
      const longitude = parseDecimal(card.longitude);
      const radius = parseDecimal(card.radius);

      if (card.latitude.trim() === '')
      {
        return problem('manca la latitudine.');
      }

      if (latitude === undefined || latitude < -90 || latitude > 90)
      {
        return problem('la latitudine va da -90 a 90.');
      }

      if (card.longitude.trim() === '')
      {
        return problem('manca la longitudine.');
      }

      if (longitude === undefined || longitude < -180 || longitude > 180)
      {
        return problem('la longitudine va da -180 a 180.');
      }

      if (card.radius.trim() === '')
      {
        return problem('manca il raggio.');
      }

      return radius === undefined || radius <= 0 ? problem('il raggio deve essere maggiore di zero.') : undefined;
    }
  }
}

/** The first problem of the proposal, if any. */
export function proposalProblem(cards: ConstraintCard[], maxDelayDays: number): string | undefined
{
  return cards.map((card, index) => cardProblem(card, index + 1, maxDelayDays)).find((problem) => problem !== undefined);
}

/** A checked card as the API takes it: only the fields of its type, so the server never drops one (R7). */
export function specOf(card: ConstraintCard): ConstraintSpec
{
  switch (card.type)
  {
    case 'TIME':
      return { assignee: card.assignee, type: 'TIME', scope: card.timeScope, notBefore: card.notBefore };
    case 'REPLY_DELAY':
      return { assignee: card.assignee, type: 'REPLY_DELAY', delay: card.delayAsItWas ?? DELAY_UNITS[card.delayUnit].iso(parseDecimal(card.delayAmount) ?? 0) };
    case 'LOCATION':
      return {
        assignee: card.assignee,
        type: 'LOCATION',
        scope: card.placeScope,
        latitude: parseDecimal(card.latitude),
        longitude: parseDecimal(card.longitude),
        radiusMetres: parseDecimal(card.radius),
        direction: card.direction,
      };
  }
}

/** A spec shown as a constraint would be, for the review's sentences. */
export function asConstraint(spec: ConstraintSpec): Constraint
{
  return {
    user: spec.assignee,
    type: spec.type,
    scope: spec.scope ?? (spec.type === 'LOCATION' ? 'READ' : 'WRITE'),
    notBefore: spec.notBefore ?? null,
    delay: spec.delay ?? null,
    latitude: spec.latitude ?? null,
    longitude: spec.longitude ?? null,
    radiusMetres: spec.radiusMetres ?? null,
    direction: spec.direction ?? null,
    description: '',
  };
}

/** The place of a card, once both coordinates are valid: what "Apri su OpenStreetMap" opens. */
export function placeOf(card: ConstraintCard): { latitude: number; longitude: number } | undefined
{
  const latitude = parseDecimal(card.latitude);
  const longitude = parseDecimal(card.longitude);

  return latitude !== undefined && longitude !== undefined && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180 ? { latitude, longitude } : undefined;
}
