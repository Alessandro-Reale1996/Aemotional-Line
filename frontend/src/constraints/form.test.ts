import { describe, expect, it } from 'vitest';

import type { ConstraintRequest } from '../api/client';
import { constraint, partner, patient, talkingCouple, therapist } from '../api/testing';
import { asConstraint, cardOf, cardProblem, cardsReducer, newCard, parseDecimal, placeOf, proposalProblem, specOf } from './form';
import { approvalLine, approveText, byPartner, constraintSentence, coordinates, mapLink } from './words';

const HOME = { latitude: 45.4642, longitude: 9.19 };

describe('a constraint in words', () =>
{
  it('says who may do what and when, in the second person for oneself', () =>
  {
    const evening = constraint(patient.id, { type: 'TIME', notBefore: '18:00:00', scope: 'BOTH' });
    expect(constraintSentence(evening, talkingCouple, patient)).toBe('Puoi aprire i messaggi e scrivere solo dalle 18:00 a mezzanotte.');
    expect(constraintSentence(evening, talkingCouple, partner)).toBe('Alex può aprire i messaggi e scrivere solo dalle 18:00 a mezzanotte.');

    const wait = constraint(partner.id, { type: 'REPLY_DELAY', delay: 'PT720H' });
    expect(constraintSentence(wait, talkingCouple, therapist)).toBe('Sam deve attendere 30 giorni dall’apertura di un messaggio prima di rispondere.');
    expect(constraintSentence(wait, talkingCouple, partner)).toBe('Devi attendere 30 giorni dall’apertura di un messaggio prima di rispondere.');
  });

  it('never names a place by its coordinates', () =>
  {
    const away = constraint(partner.id, { type: 'LOCATION', scope: 'READ', ...HOME, radiusMetres: 2000, direction: 'BEYOND' });

    expect(constraintSentence(away, talkingCouple, patient)).toBe('Sam può aprire i messaggi solo a più di 2 km dal luogo concordato.');
    expect(constraintSentence({ ...away, latitude: null, longitude: null }, talkingCouple, patient)).toBe('Sam può aprire i messaggi solo a più di 2 km dal luogo concordato.');
    expect(mapLink(45.4642, 9.19)).toBe('https://www.openstreetmap.org/?mlat=45.4642&mlon=9.19#map=16/45.4642/9.19');
    expect(coordinates(45.4642, 9.19)).toBe('45,46420, 9,19000');
  });

  it('lists the first partner’s first', () =>
  {
    const constraints = [constraint(partner.id, { notBefore: '09:00' }), constraint(patient.id, { notBefore: '10:00' }), constraint(partner.id, { notBefore: '11:00' })];

    expect(byPartner(constraints, talkingCouple).map((one) => one.notBefore)).toEqual(['10:00', '09:00', '11:00']);
  });
});

describe('the approvals of a request', () =>
{
  const pending: ConstraintRequest = {
    id: '0190a7e2-0000-7000-8000-0000000000f1',
    proposedBy: therapist.id,
    assignments: [],
    status: 'PENDING',
    approvals: [],
    proposedAt: '2026-11-17T09:00:00Z',
    closedAt: null,
    rejectedBy: null,
    previousAssignments: null,
  };

  it('say who is still missing', () =>
  {
    expect(approvalLine(pending, talkingCouple, patient)).toBe('Nessuno l’ha ancora approvata.');
    expect(approvalLine({ ...pending, approvals: [patient.id] }, talkingCouple, patient)).toBe('Hai approvato: manca l’approvazione di Sam.');
    expect(approvalLine({ ...pending, approvals: [patient.id] }, talkingCouple, partner)).toBe('Approvata da Alex: manca la tua approvazione.');
    expect(approvalLine({ ...pending, approvals: [partner.id] }, talkingCouple, therapist)).toBe('Approvata da Sam: manca l’approvazione di Alex.');
  });

  it('say whether approving applies the constraints at once', () =>
  {
    expect(approveText(pending, talkingCouple, patient)).toBe('Approvando, accetti questi vincoli: entreranno in vigore quando li avrà approvati anche Sam.');
    expect(approveText({ ...pending, approvals: [partner.id] }, talkingCouple, patient)).toBe('Approvando, questi vincoli entrano subito in vigore al posto di quelli attuali.');
  });
});

describe('the proposal’s cards', () =>
{
  it('start from the constraints in force and give them back as they were', () =>
  {
    const inForce = [
      constraint(patient.id, { type: 'TIME', notBefore: '18:30:00', scope: 'READ' }),
      constraint(partner.id, { type: 'REPLY_DELAY', delay: 'PT720H' }),
      constraint(partner.id, { type: 'REPLY_DELAY', delay: 'PT1H30M' }),
      constraint(partner.id, { type: 'LOCATION', scope: 'BOTH', ...HOME, radiusMetres: 250.5, direction: 'BEYOND' }),
    ];
    const cards = inForce.map((one) => cardOf(one, talkingCouple));

    expect(cards[0]).toMatchObject({ notBefore: '18:30', timeScope: 'READ' });
    expect(cards[1]).toMatchObject({ delayAmount: '30', delayUnit: 'DAYS' });
    expect(cards[2]).toMatchObject({ delayAmount: '90', delayUnit: 'MINUTES' });
    expect(cards[3]).toMatchObject({ latitude: '45,4642', longitude: '9,19', radius: '250,5', direction: 'BEYOND', placeScope: 'BOTH' });
    expect(proposalProblem(cards, 30)).toBeUndefined();
    expect(cards.map(specOf)).toEqual([
      { assignee: patient.id, type: 'TIME', scope: 'READ', notBefore: '18:30' },
      { assignee: partner.id, type: 'REPLY_DELAY', delay: 'PT720H' },
      { assignee: partner.id, type: 'REPLY_DELAY', delay: 'PT1H30M' },
      { assignee: partner.id, type: 'LOCATION', scope: 'BOTH', ...HOME, radiusMetres: 250.5, direction: 'BEYOND' },
    ]);
  });

  it('keep a delay that isn’t whole minutes until it is typed again', () =>
  {
    const card = cardOf(constraint(partner.id, { type: 'REPLY_DELAY', delay: 'PT3S' }), talkingCouple);
    expect(specOf(card).delay).toBe('PT3S');

    const typed = cardsReducer([card], { kind: 'edit', key: card.key, change: { delayAmount: '2', delayUnit: 'HOURS' } })[0];
    expect(typed && specOf(typed).delay).toBe('PT2H');
    const elsewhere = cardsReducer([card], { kind: 'edit', key: card.key, change: { assignee: patient.id } })[0];
    expect(elsewhere && specOf(elsewhere).delay, 'another field leaves the delay as it was').toBe('PT3S');
  });

  it('send only the fields of their type', () =>
  {
    const card = { ...newCard(talkingCouple), notBefore: '09:00', delayAmount: '2', latitude: '45', longitude: '9', radius: '100' };

    expect(specOf(card)).toEqual({ assignee: patient.id, type: 'TIME', scope: 'WRITE', notBefore: '09:00' });
    expect(specOf({ ...card, type: 'REPLY_DELAY' })).toEqual({ assignee: patient.id, type: 'REPLY_DELAY', delay: 'PT2H' });
    expect(specOf({ ...card, type: 'LOCATION' })).toEqual({
      assignee: patient.id, type: 'LOCATION', scope: 'READ', latitude: 45, longitude: 9, radiusMetres: 100, direction: 'WITHIN',
    });
    expect(asConstraint(specOf({ ...card, type: 'LOCATION' }))).toMatchObject({ user: patient.id, scope: 'READ', radiusMetres: 100 });
  });

  it('are checked before the review, naming the card', () =>
  {
    const card = newCard(talkingCouple);
    const time = { ...card, notBefore: '09:00' };

    expect(cardProblem(card, 2, 30)).toBe('Vincolo 2: manca l’orario.');
    expect(cardProblem({ ...card, type: 'REPLY_DELAY' }, 1, 30)).toBe('Vincolo 1: manca la durata.');
    expect(cardProblem({ ...card, type: 'REPLY_DELAY', delayAmount: '31', delayUnit: 'DAYS' }, 1, 30)).toBe('Vincolo 1: la durata va da 1 minuto a 30 giorni.');
    expect(cardProblem({ ...card, type: 'REPLY_DELAY', delayAmount: '1,5', delayUnit: 'HOURS' }, 1, 30)).toBe('Vincolo 1: la durata va da 1 minuto a 30 giorni.');
    expect(cardProblem({ ...card, type: 'REPLY_DELAY', delayAmount: '0', delayUnit: 'MINUTES' }, 1, 30)).toBe('Vincolo 1: la durata va da 1 minuto a 30 giorni.');
    expect(cardProblem({ ...card, type: 'REPLY_DELAY', delayAmount: '30', delayUnit: 'DAYS' }, 1, 30)).toBeUndefined();

    const place = { ...card, type: 'LOCATION' as const };
    expect(cardProblem(place, 3, 30)).toBe('Vincolo 3: manca la latitudine.');
    expect(cardProblem({ ...place, latitude: '91' }, 3, 30)).toBe('Vincolo 3: la latitudine va da -90 a 90.');
    expect(cardProblem({ ...place, latitude: '45,46' }, 3, 30)).toBe('Vincolo 3: manca la longitudine.');
    expect(cardProblem({ ...place, latitude: '45,46', longitude: '-181' }, 3, 30)).toBe('Vincolo 3: la longitudine va da -180 a 180.');
    expect(cardProblem({ ...place, latitude: '45,46', longitude: '9.19' }, 3, 30)).toBe('Vincolo 3: manca il raggio.');
    expect(cardProblem({ ...place, latitude: '45,46', longitude: '9.19', radius: '0' }, 3, 30)).toBe('Vincolo 3: il raggio deve essere maggiore di zero.');
    expect(cardProblem({ ...place, latitude: '45,46', longitude: '9.19', radius: '150' }, 3, 30)).toBeUndefined();

    expect(proposalProblem([time, card], 30)).toBe('Vincolo 2: manca l’orario.');
    expect(proposalProblem([], 30), 'an empty proposal lifts every constraint').toBeUndefined();
  });

  it('read decimals with a point or a comma, and show the place once both coordinates are valid', () =>
  {
    expect(parseDecimal(' 45,4642 ')).toBe(45.4642);
    expect(parseDecimal('-9.19')).toBe(-9.19);
    expect(parseDecimal('45°')).toBeUndefined();
    expect(placeOf({ ...newCard(talkingCouple), latitude: '45,4642', longitude: '9,19' })).toEqual(HOME);
    expect(placeOf({ ...newCard(talkingCouple), latitude: '45,4642', longitude: '' })).toBeUndefined();
  });
});
