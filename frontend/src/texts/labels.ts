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

export const PURPOSE_LABELS: Record<Schemas['NegotiationWaiting']['purpose'], string> = {
  STARTED_CONVERSATION: 'Stipula dell’accordo',
  MODIFIED_AGREEMENT: 'Modifica dell’accordo',
  OPENED_DISCUSSION: 'Richiesta di un discorso specifico',
};
