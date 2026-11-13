import type { components } from './schema';

/** Every code the API can answer with (CLAUDE.md, R1): the app picks its Italian text by it. */
export type ErrorCode = components['schemas']['Problem']['code'];

/** The codes that mean the app sent something the interface shouldn't allow (frontend point 4). */
const APP_MISTAKES = ['ACCESS_DENIED', 'MALFORMED_REQUEST', 'RULE_VIOLATED'];

/**
 * What a call to the API can fail with (CLAUDE.md, frontend point 2): one of the API's errors, with its code, or no
 * answer at all. "No answer" also covers an answer that isn't one of the API's errors, a reverse proxy's error page for
 * instance: to the user it is the same, the server can't be reached.
 */
export class ApiError extends Error
{
  private constructor(
    /** The HTTP status, when an answer arrived. */
    readonly status: number | undefined,
    /** The API's code; undefined when the server couldn't be reached. A string, since a newer server may send a code this app doesn't know. */
    readonly code: string | undefined,
    /** Given with an unexpected error (500), for the user to report. */
    readonly errorId: string | undefined,
    message: string,
  )
  {
    super(message);
    this.name = 'ApiError';
  }

  /** The server can't be reached, or answered with something that isn't one of its errors. */
  static unreachable(status?: number): ApiError
  {
    return new ApiError(status, undefined, undefined, 'The server could not be reached.');
  }

  /** An answer that isn't a success: one of the API's Problem Details, or unreachable. */
  static fromAnswer(status: number, body: unknown): ApiError
  {
    if (!isProblem(body))
    {
      return ApiError.unreachable(status);
    }

    // Only in development, and only the app's own mistakes: the console holds no content (frontend points 4 and 7).
    if (import.meta.env.DEV && APP_MISTAKES.includes(body.code))
    {
      console.error(`${body.code}: ${body.detail ?? ''}`);
    }

    return new ApiError(status, body.code, typeof body.errorId === 'string' ? body.errorId : undefined, `${status} ${body.code}`);
  }

  /** Reads an answer that isn't a success. */
  static async fromResponse(response: Response): Promise<ApiError>
  {
    const text = await response.text().catch(() => '');
    let body: unknown;

    try
    {
      body = JSON.parse(text);
    }
    catch
    {
      body = undefined;
    }

    return ApiError.fromAnswer(response.status, body);
  }

  /**
   * What a call threw: an ApiError stays itself, a failed fetch means the server can't be reached. Anything else is a
   * mistake in the app and is thrown again, so that it isn't hidden behind a "try again".
   */
  static from(failure: unknown): ApiError
  {
    if (failure instanceof ApiError)
    {
      return failure;
    }

    if (failure instanceof TypeError || failure instanceof DOMException)
    {
      return ApiError.unreachable();
    }

    throw failure;
  }

  /** True when the server couldn't be reached. */
  get unreachable(): boolean
  {
    return this.code === undefined;
  }
}

function isProblem(body: unknown): body is { code: string; detail?: string; errorId?: unknown }
{
  return typeof body === 'object' && body !== null && typeof (body as { code?: unknown }).code === 'string';
}
