import { describe, expect, it } from 'vitest';

import { ApiError } from '../api/errors';
import { ERROR_TEXTS, errorText, GENERIC, UNREACHABLE } from './errors';

describe('the error texts', () =>
{
  it('give each code its own text, the error id of an unexpected error included', () =>
  {
    expect(errorText(ApiError.fromAnswer(409, { code: 'NOT_YOUR_TURN' }))).toBe('Ora tocca all’altra persona.');
    expect(errorText(ApiError.fromAnswer(500, { code: 'INTERNAL_ERROR', errorId: 'e-42' }))).toBe(
      'Si è verificato un errore imprevisto. Riprova tra qualche minuto; se si ripete, segnala questo codice: e-42.');
  });

  it('say "reload and retry" for the app own mistakes and for a code the app does not know', () =>
  {
    for (const code of ['ACCESS_DENIED', 'MALFORMED_REQUEST', 'RULE_VIOLATED', 'A_CODE_OF_A_NEWER_SERVER'])
    {
      expect(errorText(ApiError.fromAnswer(400, { code }))).toBe(GENERIC);
    }
  });

  it('say that the server does not answer when nothing came back', () =>
  {
    expect(errorText(ApiError.unreachable())).toBe(UNREACHABLE);
    expect(errorText(ApiError.fromAnswer(502, '<html>'))).toBe(UNREACHABLE);
  });

  it('are never empty, never shout and never show a placeholder', () =>
  {
    for (const [code, text] of Object.entries(ERROR_TEXTS))
    {
      expect(text.trim(), code).not.toBe('');
      expect(text, code).not.toContain('!');
      expect(text, code).not.toContain("'");

      if (code !== 'INTERNAL_ERROR')
      {
        expect(text, code).not.toMatch(/[{}]/);
      }
    }
  });
});
