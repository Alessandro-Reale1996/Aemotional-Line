import { type LoaderFunctionArgs, redirect, type RouteObject } from 'react-router';

import { AccountPage } from '../account/AccountPage';
import { AdministrationPage } from '../admin/AdministrationPage';
import { TherapistPage } from '../admin/TherapistPage';
import { defaultPeriod, periodInstants, periodProblem } from '../archive/archive';
import {
  ARCHIVE_ROUTE,
  ArchiveLayout,
  ConcludedNegotiationsPage,
  NoArchivePage,
  PeriodPage,
  QuestionsPage,
  SearchPage,
  TopicPage,
} from '../archive/ArchivePages';
import { type Account, ask, type Couple, type Role } from '../api/client';
import { ApiError } from '../api/errors';
import { ForgottenPasswordPage } from '../auth/ForgottenPasswordPage';
import { LoginPage } from '../auth/LoginPage';
import { NewPasswordPage } from '../auth/NewPasswordPage';
import { Loading } from '../components/frames';
import { ConstraintsPage } from '../constraints/ConstraintsPage';
import { ConversationPage, DiscussionPage, NoConversationPage } from '../conversation/ConversationPages';
import { indexParagraphs, paragraphAddress } from '../conversation/Letters';
import { CouplePage } from '../couples/CouplePage';
import { HomePage } from '../home/HomePage';
import { NewInvitationPage } from '../invitations/NewInvitationPage';
import { NegotiationPage } from '../negotiations/NegotiationPage';
import { NewAgreementPage, NewDiscussionPage } from '../negotiations/NewNegotiationPages';
import { unlessUnsentText } from '../session/unsent';
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

  /** One of the user's couples, or "not found". */
  async function coupleWhere(matches: (couple: Couple) => boolean): Promise<Couple>
  {
    const couple = (await couples()).find(matches);

    if (!couple)
    {
      throw notFound();
    }

    return couple;
  }

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
                    const couple = await coupleWhere((candidate) => candidate.id === params.id);
                    const id = { params: { path: { id: couple.id } } };
                    const [negotiations, failedStarts] = await Promise.all([
                      ask(api.GET('/api/couples/{id}/negotiations', id)),
                      couple.conversation ? Promise.resolve([]) : ask(api.GET('/api/couples/{id}/failed-starts', id)),
                    ]);

                    return { couple, negotiations, failedStarts };
                  }),
                  element: <CouplePage />,
                },
                {
                  path: 'coppie/:id/nuovo-accordo',
                  loader: page(['PATIENT'], async (_, { params }) =>
                    ({ couple: await coupleWhere((candidate) => candidate.id === params.id), limits: await limits() })),
                  shouldRevalidate: unlessUnsentText,
                  element: <NewAgreementPage />,
                },
                {
                  path: 'conversazioni/:id/nuovo-discorso',
                  loader: page(['PATIENT'], async (_, { params }) =>
                    ({ couple: await coupleWhere((candidate) => candidate.conversation === params.id), limits: await limits() })),
                  shouldRevalidate: unlessUnsentText,
                  element: <NewDiscussionPage />,
                },
                {
                  path: 'conversazione',
                  loader: page(['PATIENT'], async () =>
                  {
                    const current = (await couples()).find((couple) => couple.active && couple.conversation);

                    if (current?.conversation)
                    {
                      throw redirect(`/conversazioni/${current.conversation}`);
                    }

                    return {};
                  }),
                  element: <NoConversationPage />,
                },
                {
                  path: 'conversazioni/:id',
                  loader: page(['PATIENT', 'THERAPIST'], async (_, { params }) =>
                  {
                    const [conversation, couple] = await Promise.all([
                      ask(api.GET('/api/conversations/{id}', { params: { path: { id: params.id ?? '' } } })),
                      coupleWhere((candidate) => candidate.conversation === params.id),
                    ]);

                    return { conversation, couple };
                  }),
                  element: <ConversationPage />,
                },
                {
                  path: 'conversazioni/:id/discorsi/:n',
                  loader: page(['PATIENT', 'THERAPIST'], async (_, { params }) =>
                  {
                    const id = { params: { path: { id: params.id ?? '' } } };
                    const [conversation, couple, constraints] = await Promise.all([
                      ask(api.GET('/api/conversations/{id}', id)),
                      coupleWhere((candidate) => candidate.conversation === params.id),
                      ask(api.GET('/api/conversations/{id}/constraints', id)),
                    ]);

                    if (!conversation.discussions.some((discussion) => String(discussion.id) === params.n))
                    {
                      throw notFound();
                    }

                    const number = Number(params.n);
                    // The main discussion has no closing (CLAUDE.md, gap 4).
                    const [negotiations, theLimits, closing] = await Promise.all([
                      ask(api.GET('/api/couples/{id}/negotiations', { params: { path: { id: couple.id } } })),
                      limits(),
                      number === 0
                        ? Promise.resolve(undefined)
                        : ask(api.GET('/api/conversations/{id}/discussions/{number}/closing', { params: { path: { id: conversation.id, number } } })),
                    ]);

                    return { conversation, couple, inForce: constraints.inForce, negotiations, limits: theLimits, closing };
                  }),
                  shouldRevalidate: unlessUnsentText,
                  element: <DiscussionPage />,
                },
                {
                  path: 'conversazioni/:id/vincoli',
                  loader: page(['PATIENT', 'THERAPIST'], async (_, { params }) =>
                  {
                    const [constraints, couple, theLimits] = await Promise.all([
                      ask(api.GET('/api/conversations/{id}/constraints', { params: { path: { id: params.id ?? '' } } })),
                      coupleWhere((candidate) => candidate.conversation === params.id),
                      limits(),
                    ]);

                    return { conversation: params.id ?? '', couple, constraints, limits: theLimits };
                  }),
                  shouldRevalidate: unlessUnsentText,
                  element: <ConstraintsPage />,
                },
                {
                  path: 'archivio',
                  // The archive of the couple in progress, otherwise of the latest ended one (F10 decision 5).
                  loader: page(['PATIENT'], async () =>
                  {
                    const found = (await couples()).find((couple) => couple.conversation);

                    if (found?.conversation)
                    {
                      throw redirect(`/conversazioni/${found.conversation}/archivio`);
                    }

                    return {};
                  }),
                  element: <NoArchivePage />,
                },
                {
                  id: ARCHIVE_ROUTE,
                  path: 'conversazioni/:id/archivio',
                  loader: page(['PATIENT', 'THERAPIST'], async (_, { params }) =>
                  {
                    const [conversation, couple] = await Promise.all([
                      ask(api.GET('/api/conversations/{id}', { params: { path: { id: params.id ?? '' } } })),
                      coupleWhere((candidate) => candidate.conversation === params.id),
                    ]);

                    return { conversation, couple };
                  }),
                  element: <ArchiveLayout />,
                  children: [
                    { index: true, element: <SearchPage /> },
                    {
                      path: 'periodo',
                      loader: page(['PATIENT', 'THERAPIST'], async (_, { params, request }) =>
                      {
                        const search = new URL(request.url).searchParams;
                        const fallback = defaultPeriod(new Date());
                        const dal = search.get('dal') ?? fallback.dal;
                        const al = search.get('al') ?? fallback.al;
                        const problem = periodProblem(dal, al);

                        if (problem)
                        {
                          return { dal, al, problem, entries: [] };
                        }

                        const entries = await ask(api.GET('/api/conversations/{id}/archive/messages', {
                          params: { path: { id: params.id ?? '' }, query: periodInstants(dal, al) },
                        }));

                        return { dal, al, entries };
                      }),
                      element: <PeriodPage />,
                    },
                    {
                      path: 'domande',
                      loader: page(['PATIENT', 'THERAPIST'], (_, { params }) =>
                        ask(api.GET('/api/conversations/{id}/archive/unanswered-questions', { params: { path: { id: params.id ?? '' } } }))),
                      element: <QuestionsPage />,
                    },
                    {
                      path: 'stipule',
                      loader: page(['PATIENT', 'THERAPIST'], (_, { params }) =>
                        ask(api.GET('/api/conversations/{id}/archive/negotiations', { params: { path: { id: params.id ?? '' } } }))),
                      element: <ConcludedNegotiationsPage />,
                    },
                    {
                      path: 'argomenti/:paragrafo',
                      loader: page(['PATIENT', 'THERAPIST'], (_, { params }) =>
                        ask(api.GET('/api/conversations/{id}/archive/topics/{paragraphId}', {
                          params: { path: { id: params.id ?? '', paragraphId: params.paragrafo ?? '' } },
                        }))),
                      element: <TopicPage />,
                    },
                  ],
                },
                {
                  path: 'stipule/:id',
                  loader: page(['PATIENT', 'THERAPIST'], async (_, { params }) =>
                  {
                    const [negotiation, theLimits] = await Promise.all([
                      ask(api.GET('/api/negotiations/{id}', { params: { path: { id: params.id ?? '' } } })),
                      limits(),
                    ]);
                    const couple = await coupleWhere((candidate) => candidate.id === negotiation.coupleId);
                    let origin;

                    // The paragraph a discussion expands, from the conversation as this user sees it (F7).
                    if (negotiation.originParagraphId && couple.conversation)
                    {
                      const conversation = await ask(api.GET('/api/conversations/{id}', { params: { path: { id: couple.conversation } } }));
                      const found = indexParagraphs(conversation).get(negotiation.originParagraphId);
                      origin = found
                        ? { title: found.title, address: paragraphAddress(conversation.id, found.discussion, negotiation.originParagraphId) }
                        : undefined;
                    }

                    return { negotiation, couple, limits: theLimits, origin };
                  }),
                  shouldRevalidate: unlessUnsentText,
                  element: <NegotiationPage />,
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
