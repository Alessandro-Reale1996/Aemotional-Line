import type { Limits } from '../api/client';
import { ERROR_TEXTS } from '../texts/errors';

/** The rule shown under a new password (texts approved on 2026-11-13), with the numbers of GET /api/limits. */
export function passwordRule(limits: Limits): string
{
  return `Da ${limits.passwordMinLength} a ${limits.passwordMaxLength} caratteri, né una password comune né il tuo indirizzo email. Puoi usare una frase di più parole.`;
}

/**
 * What the app can check before sending a new password (frontend point 4): its length, counted in code points like the
 * server, and the bytes bcrypt keeps. Whether it is a common password, or the account's email, only the server knows.
 */
export function newPasswordProblem(password: string, limits: Limits): string | undefined
{
  const length = [...password].length;

  if (length === 0)
  {
    return MISSING_PASSWORD;
  }

  if (length < limits.passwordMinLength)
  {
    return ERROR_TEXTS.PASSWORD_TOO_SHORT;
  }

  if (length > limits.passwordMaxLength || new TextEncoder().encode(password).length > limits.passwordMaxBytes)
  {
    return ERROR_TEXTS.PASSWORD_TOO_LONG;
  }

  return undefined;
}

export const MISSING_EMAIL = 'Manca l’indirizzo email.';
export const MISSING_PASSWORD = 'Manca la password.';
