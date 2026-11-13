import type { RouteObject } from 'react-router';

import { ask } from '../api/client';
import { ForgottenPasswordPage } from '../auth/ForgottenPasswordPage';
import { LoginPage } from '../auth/LoginPage';
import { NewPasswordPage } from '../auth/NewPasswordPage';
import { Loading } from '../components/frames';
import { HomePage } from '../home/HomePage';
import type { App, RootData } from './context';
import { AppLayout, ErrorPage, NotFoundPage, Root } from './Root';

/**
 * The app's pages (CLAUDE.md, frontend point 5): addresses in Italian, holding ids only. The root reads who is logged in,
 * once per page load: a login or a logout reloads the page, so it never needs reading again.
 */
export function createRoutes({ api, session }: App): RouteObject[]
{
  const limits = () => ask(api.GET('/api/limits'));

  return [
    {
      id: 'root',
      path: '/',
      loader: async (): Promise<RootData> =>
      {
        const account = await api.whoIsLoggedIn();
        session.setAccount(account);

        return { account };
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
        { element: <AppLayout />, children: [{ index: true, element: <HomePage /> }] },
        { path: '*', element: <NotFoundPage /> },
      ],
    },
  ];
}
