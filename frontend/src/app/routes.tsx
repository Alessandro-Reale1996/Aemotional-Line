import { type LoaderFunctionArgs, redirect, type RouteObject } from 'react-router';

import { AccountPage } from '../account/AccountPage';
import { AdministrationPage } from '../admin/AdministrationPage';
import { TherapistPage } from '../admin/TherapistPage';
import { type Account, ask, type Role } from '../api/client';
import { ApiError } from '../api/errors';
import { ForgottenPasswordPage } from '../auth/ForgottenPasswordPage';
import { LoginPage } from '../auth/LoginPage';
import { NewPasswordPage } from '../auth/NewPasswordPage';
import { Loading } from '../components/frames';
import { CouplePage } from '../couples/CouplePage';
import { HomePage } from '../home/HomePage';
import { NewInvitationPage } from '../invitations/NewInvitationPage';
import type { App, RootData } from './context';
import { AppLayout, ErrorPage, NotFoundPage, PageError, Root } from './Root';

const EVERYONE: readonly Role[] = ['PATIENT', 'THERAPIST', 'ADMINISTRATOR'];

function notFound(): ApiError
{
  return ApiError.fromAnswer(404, { code: 'NOT_FOUND' });
}

/**
 * The app's pages (CLAUDE.md, frontend point 5): addresses in Italian, holding ids only. Who is logged in is asked once
 * per page load, by the root and by every page that needs it: a login or a logout reloads the page, so it never changes
 * in between, and the loaders of a page, which run together, all wait for the same answer.
 */
export function createRoutes({ api, session }: App): RouteObject[]
{
  let account: Promise<Account | null> | undefined;
  const whoAmI = () => (account ??= api.whoIsLoggedIn());
  const limits = () => ask(api.GET('/api/limits'));
  const couples = () => ask(api.GET('/api/me/couples'));

  /** The data of a page for the given roles. A page of another role reads as "not found" (F5 decision 2). */
  function page<T>(roles: readonly Role[], load: (me: Account, args: LoaderFunctionArgs) => Promise<T>)
  {
    return async (args: LoaderFunctionArgs): Promise<T | null> =>
    {
      const me = await whoAmI();

      if (!me)
      {
        // AppLayout sends to the login.
        return null;
      }

      if (!roles.includes(me.role))
      {
        throw notFound();
      }

      return load(me, args);
    };
  }

  return [
    {
      id: 'root',
      path: '/',
      loader: async (): Promise<RootData> =>
      {
        const me = await whoAmI();
        session.setAccount(me);

        return { account: me };
      },
      shouldRevalidate: () => false,
      element: <Root />,
      errorElement: <ErrorPage />,
      HydrateFallback: Loading,
      children: [
        { path: 'accedi', element: <LoginPage /> },
        { path: 'password-dimenticata', element: <ForgottenPasswordPage /> },
        { path: 'attiva', loader: limits, element: <NewPasswordPage kind="activation" /> },
        { path: 'reimposta-password', loader: limits, element: <NewPasswordPage kind="reset" /> },
        {
          element: <AppLayout />,
          children: [
            {
              errorElement: <PageError />,
              children: [
                {
                  index: true,
                  loader: page(EVERYONE, async (me) =>
                  {
                    if (me.role === 'ADMINISTRATOR')
                    {
                      throw redirect('/amministrazione');
                    }

                    const [summary, mine] = await Promise.all([ask(api.GET('/api/me/summary')), couples()]);

                    return { summary, couples: mine };
                  }),
                  element: <HomePage />,
                },
                {
                  path: 'account',
                  loader: page(EVERYONE, async (me) => ({ couples: me.role === 'PATIENT' ? await couples() : [] })),
                  element: <AccountPage />,
                },
                { path: 'inviti/nuovo', loader: page(['THERAPIST'], limits), element: <NewInvitationPage /> },
                {
                  path: 'coppie/:id',
                  loader: page(['PATIENT', 'THERAPIST'], async (_, { params }) =>
                  {
                    const couple = (await couples()).find((candidate) => candidate.id === params.id);

                    if (!couple)
                    {
                      throw notFound();
                    }

                    return couple;
                  }),
                  element: <CouplePage />,
                },
                {
                  path: 'amministrazione',
                  loader: page(['ADMINISTRATOR'], async () =>
                  {
                    const [therapists, theLimits] = await Promise.all([ask(api.GET('/api/admin/therapists')), limits()]);

                    return { therapists, limits: theLimits };
                  }),
                  element: <AdministrationPage />,
                },
                {
                  path: 'amministrazione/terapeuti/:id',
                  loader: page(['ADMINISTRATOR'], async (_, { params }) =>
                  {
                    const therapist = (await ask(api.GET('/api/admin/therapists'))).find((candidate) => candidate.id === params.id);

                    if (!therapist || therapist.status !== 'DISABLED')
                    {
                      throw notFound();
                    }

                    const ended = await ask(api.GET('/api/admin/therapists/{id}/ended-couples', { params: { path: { id: therapist.id } } }));

                    return { therapist, ended };
                  }),
                  element: <TherapistPage />,
                },
              ],
            },
          ],
        },
        { path: '*', element: <NotFoundPage /> },
      ],
    },
  ];
}
