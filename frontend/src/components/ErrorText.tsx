import type { ApiError } from '../api/errors';
import { errorText } from '../texts/errors';

/** Why an action was refused, shown next to its button (CLAUDE.md, frontend point 4). A page may add a hint of its own. */
export function ErrorText({ error, hint }: { error: ApiError | string | undefined; hint?: string })
{
  if (error === undefined)
  {
    return null;
  }

  const text = typeof error === 'string' ? error : errorText(error);

  return (
    <div className="text-danger mt-2" role="alert">
      {hint ? `${text} ${hint}` : text}
    </div>
  );
}
