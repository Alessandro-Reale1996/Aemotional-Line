import type { components } from '../api/schema';
import type { Role } from '../api/client';

type Schemas = components['schemas'];

/**
 * The Italian names of the values the API sends by name (CLAUDE.md, frontend point 4), typed by openapi.json: a new value
 * doesn't compile until it has its name. Each step adds the sets its screens show (approved with them).
 */
export const ROLE_LABELS: Record<Role, string> = {
  THERAPIST: 'Terapeuta',
  PATIENT: 'Paziente',
  ADMINISTRATOR: 'Amministrazione',
};

export const ACCOUNT_STATUS_LABELS: Record<Schemas['TherapistView']['status'], string> = {
  PENDING: 'In attesa di attivazione',
  ACTIVE: 'Attivo',
  DISABLED: 'Disattivato',
};

export const INVITATION_STATUS_LABELS: Record<Schemas['InvitationView']['status'], string> = {
  PENDING: 'In attesa',
  ACCEPTED: 'Accettato',
  DECLINED: 'Rifiutato',
  WITHDRAWN: 'Ritirato',
  EXPIRED: 'Scaduto',
};

export const NEGOTIATION_STATUS_LABELS: Record<Schemas['NegotiationView']['status'], string> = {
  DRAFT: 'Richiesta in attesa di risposta',
  ACCEPTED: 'In corso',
  REFUSED: 'Richiesta rifiutata',
  ABANDONED: 'Abbandonata',
};

export const PROPOSAL_STATUS_LABELS: Record<Schemas['ProposalView']['status'], string> = {
  DRAFT: 'Bozza',
  WAITING_FOR_RESPONSE: 'In attesa di risposta',
  REFUSED: 'Rifiutata',
  ACCEPTED: 'Accettata',
};

export const PARAGRAPH_TYPE_LABELS: Record<Schemas['ParagraphView']['type'], string> = {
  SIMPLE: 'Paragrafo',
  QUESTION: 'Domanda',
  POINTED: 'Risposta puntuale',
};

export const PURPOSE_LABELS: Record<Schemas['NegotiationWaiting']['purpose'], string> = {
  STARTED_CONVERSATION: 'Stipula dell’accordo',
  MODIFIED_AGREEMENT: 'Modifica dell’accordo',
  OPENED_DISCUSSION: 'Richiesta di un discorso specifico',
};

export const CLOSING_REQUEST_STATUS_LABELS: Record<Schemas['ClosingRequestView']['status'], string> = {
  PENDING: 'In attesa di risposta',
  ACCEPTED: 'Accettata',
  REFUSED: 'Rifiutata',
  WITHDRAWN: 'Ritirata',
};

export const THERAPIST_REQUEST_STATUS_LABELS: Record<Schemas['TherapistRequestView']['status'], string> = {
  PENDING: 'In attesa della decisione',
  CONFIRMED: 'Accolta',
  REFUSED: 'Non accolta',
};

export const OUTCOME_LABELS: Record<Schemas['Declaration']['outcome'], string> = {
  RESOLVED: 'Risolto',
  STALLED: 'Arenato',
};

export const CONSTRAINT_TYPE_LABELS: Record<Schemas['ConstraintView']['type'], string> = {
  TIME: 'Orario',
  REPLY_DELAY: 'Attesa prima di rispondere',
  LOCATION: 'Luogo',
};

export const SCOPE_LABELS: Record<Schemas['ConstraintView']['scope'], string> = {
  READ: 'Lettura',
  WRITE: 'Scrittura',
  BOTH: 'Lettura e scrittura',
};

export const DIRECTION_LABELS: Record<NonNullable<Schemas['ConstraintView']['direction']>, string> = {
  WITHIN: 'Entro il raggio',
  BEYOND: 'Oltre il raggio',
};

export const CONSTRAINT_REQUEST_STATUS_LABELS: Record<Schemas['RequestView']['status'], string> = {
  PENDING: 'In attesa di approvazione',
  REJECTED: 'Rifiutata',
  WITHDRAWN: 'Ritirata',
  APPLIED: 'Applicata',
};
