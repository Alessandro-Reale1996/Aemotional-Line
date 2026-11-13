import { ERROR_TEXTS } from '../texts/errors';
import type { EndReason } from './navigation';

/** What the login page says about why it is shown (texts approved on 2026-11-13). */
export const END_NOTICES: Record<EndReason, string> = {
  expired: ERROR_TEXTS.AUTHENTICATION_REQUIRED,
  inactivity: 'La sessione è stata chiusa dopo 30 minuti di inattività, per proteggere la tua riservatezza.',
  elsewhere: 'La sessione è stata chiusa in un’altra scheda.',
  logout: 'Hai chiuso la sessione.',
  deleted: 'L’account è stato eliminato.',
};

export const OVERLAY_NOTICE =
  'La sessione è terminata. Accedi di nuovo per riprendere da dove eri: il testo che stavi scrivendo è ancora qui. Se accedi con un altro account, andrà perso.';
export const EXIT_WITH_TEXT = 'Hai del testo non inviato: uscendo andrà perso. Vuoi uscire comunque?';
export const LEAVE_WITH_TEXT = 'Hai del testo non inviato: lasciando la pagina andrà perso.';
export const SHARED_DEVICE = 'Se usi un dispositivo condiviso, non salvare la password e ricordati di uscire.';
