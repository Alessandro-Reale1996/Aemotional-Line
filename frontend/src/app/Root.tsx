import { useEffect } from 'react';
import { Navigate, Outlet, useLoaderData, useLocation, useRouteError } from 'react-router';

import { ApiError } from '../api/errors';
import { AppFrame, ButtonLink, PublicFrame } from '../components/frames';
import type { LoginState } from '../session/navigation';
import { ERROR_TEXTS, errorText, GENERIC } from '../texts/errors';
import { type RootData, useAccount, useApp } from './context';

/** The root of every page: gives the session its account and lets it listen to the page and to the other tabs. */
export function Root()
{
  const { account } = useLoaderData() as RootData;
  const { session } = useApp();

  useEffect(() =>
  {
    session.setAccount(account);

    return session.connect(window);
  }, [session, account]);

  return <Outlet />;
}

/** The pages of a logged-in user; without a session, the login, which comes back here afterwards. */
export function AppLayout()
{
  const account = useAccount();
  const location = useLocation();

  if (!account)
  {
    return <Navigate to="/accedi" replace state={{ returnTo: location.pathname + location.search } satisfies LoginState} />;
  }

  return (
    <AppFrame account={account}>
      <Outlet />
    </AppFrame>
  );
}

/** An address the app doesn't know: the same text as anything that doesn't exist or isn't available (frontend point 4). */
export function NotFoundPage()
{
  const account = useAccount();
  const content = (
    <>
      <p>{ERROR_TEXTS.NOT_FOUND}</p>
      <ButtonLink to="/" variant="outline-primary">
        Torna all’inizio
      </ButtonLink>
    </>
  );

  return account ? (
    <AppFrame account={account}>
      <h1 className="h4 mb-3">Pagina non trovata</h1>
      {content}
    </AppFrame>
  ) : (
    <PublicFrame title="Pagina non trovata">{content}</PublicFrame>
  );
}

/**
 * What a page shows when its data couldn't be read (frontend point 4): the server not answering, an unexpected error with
 * its code, or a mistake of the app. Without a session, the login, which comes back afterwards.
 */
export function ErrorPage()
{
  const error = useRouteError();
  const location = useLocation();
  const failure = error instanceof ApiError ? error : undefined;

  if (failure?.code === 'AUTHENTICATION_REQUIRED')
  {
    return <Navigate to="/accedi" replace state={{ returnTo: location.pathname + location.search } satisfies LoginState} />;
  }

  return (
    <PublicFrame title="Qualcosa non ha funzionato">
      <p role="alert">{failure ? errorText(failure) : GENERIC}</p>
      <button type="button" className="btn btn-primary" onClick={() => window.location.reload()}>
        Riprova
      </button>
    </PublicFrame>
  );
}
