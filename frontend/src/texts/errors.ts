import type { ApiError, ErrorCode } from '../api/errors';

/**
 * What the user reads when the API refuses something (CLAUDE.md, frontend point 4; texts approved on 2026-11-13). One text
 * per code, typed by the codes in openapi.json: a new code doesn't compile until it has its text. `detail` is never shown.
 * Short and neutral, "tu", no exclamation marks, no blame, no gendered form for the user; typographic apostrophes (’).
 */
export const GENERIC = 'Non è stato possibile completare l’operazione. Ricarica la pagina e riprova.';
export const UNREACHABLE = 'Il server non risponde. Riprova tra qualche minuto.';

export const ERROR_TEXTS: Record<ErrorCode, string> = {
  // Access and requests
  AUTHENTICATION_REQUIRED: 'La sessione è scaduta. Accedi di nuovo per continuare.',
  INVALID_CREDENTIALS: 'Email o password non validi. Dopo 5 tentativi sbagliati di seguito l’accesso resta bloccato per 15 minuti.',
  ACCESS_DENIED: GENERIC,
  MALFORMED_REQUEST: GENERIC,
  RULE_VIOLATED: GENERIC,
  NOT_FOUND: 'Quello che cerchi non esiste o non è disponibile.',
  INTERNAL_ERROR: 'Si è verificato un errore imprevisto. Riprova tra qualche minuto; se si ripete, segnala questo codice: {errorId}.',

  // Links and passwords
  INVALID_LINK: 'Il link non è valido: forse è scaduto o è già stato usato.',
  PASSWORD_TOO_SHORT: 'La password è troppo corta.',
  PASSWORD_TOO_LONG: 'La password è troppo lunga.',
  PASSWORD_TOO_EASY: 'Questa password è troppo facile da indovinare: è molto comune o coincide con l’email. Scegline un’altra.',
  INCORRECT_PASSWORD: 'La password non è corretta. Dopo 5 tentativi sbagliati di seguito l’account resta bloccato per 15 minuti.',

  // Accounts, invitations, couples and roles
  INVALID_EMAIL: 'L’indirizzo email non è valido.',
  EMAIL_ALREADY_USED: 'Esiste già un account con questo indirizzo email.',
  SAME_EMAIL_TWICE: 'Le due persone devono avere indirizzi email diversi.',
  CANNOT_BE_INVITED: 'Una delle due persone non può essere invitata in questo momento.',
  PARTNER_IN_ACTIVE_COUPLE: 'Non è possibile accettare: una delle due persone fa già parte di un percorso di coppia in corso.',
  INVITATION_ALREADY_ACCEPTED: 'Hai già accettato questo invito.',
  INVITATION_CLOSED: 'Questo invito si è già concluso.',
  INVITATION_EXPIRED: 'Questo invito è scaduto.',
  ACCOUNT_IN_ACTIVE_COUPLE: 'Puoi eliminare l’account solo quando il percorso di coppia è concluso.',
  COUPLE_ENDED: 'Il percorso di questa coppia è concluso: ora si può solo consultare.',
  COUPLE_NOT_ENDED: 'I dati si possono eliminare solo dopo la conclusione del percorso.',
  NOT_A_PARTNER: 'Questa azione spetta alle due persone della coppia.',
  NOT_THE_THERAPIST: 'Questa azione spetta a chi segue la coppia come terapeuta.',
  // These two come only from the command on the server's terminal: the browser never sees them.
  ADMINISTRATOR_EXISTS: 'Esiste già un amministratore.',
  ADMINISTRATOR_PENDING: 'C’è già un amministratore in attesa di attivazione.',

  // Negotiations and messages
  AGREEMENT_NEGOTIATION_IN_PROGRESS: 'È già in corso una stipula dell’accordo.',
  NEGOTIATION_WRONG_PHASE: 'Questa azione non è possibile in questa fase della stipula.',
  NEGOTIATION_ENDED: 'Questa stipula si è già conclusa.',
  NOT_YOUR_TURN: 'Ora tocca all’altra persona.',
  EXPANSION_ALREADY_REQUESTED: 'È già in corso una richiesta di approfondire questo paragrafo.',
  PARAGRAPH_ALREADY_EXPANDED: 'Questo paragrafo è già approfondito in un discorso specifico.',
  MESSAGE_NOT_OPENED: 'Per rispondere, apri prima il messaggio.',
  MESSAGE_EMPTY: 'Il messaggio deve avere almeno un paragrafo.',
  MESSAGE_TOO_LONG: 'Il messaggio supera il numero massimo di paragrafi.',
  TEXT_REQUIRED: 'Un campo obbligatorio è vuoto.',
  TEXT_TOO_LONG: 'Un testo supera la lunghezza massima.',
  REFERENCE_NOT_ALLOWED: 'Si può rispondere solo ai paragrafi del messaggio ricevuto per ultimo.',
  ORIGIN_REFERENCE_REQUIRED: 'Nel primo messaggio di un discorso specifico ogni paragrafo risponde al paragrafo che si approfondisce.',
  PARAGRAPH_EXPANDED_ELSEWHERE: 'A questo paragrafo si risponde nel discorso specifico che lo approfondisce.',
  QUESTION_NEEDS_POINTED_ANSWER: 'A una domanda si risponde con un paragrafo puntuale.',
  QUESTION_ALREADY_ANSWERED: 'Questa domanda ha già una risposta.',
  SELF_CITATION_NOT_ALLOWED: 'Puoi citare solo un tuo paragrafo dello stesso argomento.',

  // Closings and constraints
  DISCUSSION_CLOSED: 'Questo discorso è chiuso: si può solo consultare.',
  DISCUSSION_NOT_CLOSED: 'L’esito si dichiara quando il discorso è chiuso.',
  CLOSING_REQUEST_PENDING: 'C’è già una richiesta di chiusura in sospeso per questo discorso.',
  NO_PENDING_CLOSING_REQUEST: 'Non c’è nessuna richiesta di chiusura in sospeso.',
  THERAPIST_REQUEST_NOT_ALLOWED_YET: 'Prima di coinvolgere chi vi segue in terapia, prova a chiudere il discorso con l’altra persona.',
  OUTCOME_ALREADY_DECLARED: 'Hai già dichiarato l’esito di questo discorso.',
  CONSTRAINT_NOT_SATISFIED: 'In questo momento un vincolo concordato non lo permette.',
  CONSTRAINT_REQUEST_PENDING: 'C’è già una proposta di vincoli in attesa di risposta.',
  CONSTRAINT_REQUEST_CLOSED: 'Questa proposta di vincoli si è già conclusa.',
  INVALID_CONSTRAINT: 'Un vincolo non è valido: controlla i valori inseriti.',
};

/** The text for a failed call: by its code, the generic one for a code this app doesn't know. */
export function errorText(error: ApiError): string
{
  if (error.code === undefined)
  {
    return UNREACHABLE;
  }

  const text = (ERROR_TEXTS as Partial<Record<string, string>>)[error.code] ?? GENERIC;

  return text.replace('{errorId}', error.errorId ?? '');
}
