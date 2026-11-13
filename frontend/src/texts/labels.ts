import type { Role } from '../api/client';

/**
 * The Italian names of the values the API sends by name (CLAUDE.md, frontend point 4), typed by openapi.json: a new value
 * doesn't compile until it has its name. Each step adds the sets its screens show; F4 shows only the roles.
 */
export const ROLE_LABELS: Record<Role, string> = {
  THERAPIST: 'Terapeuta',
  PATIENT: 'Paziente',
  ADMINISTRATOR: 'Amministrazione',
};
