import 'bootstrap/dist/css/bootstrap.min.css';
import './styles.css';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { createApi } from './api/client';
import { AppContext } from './app/context';
import { createRoutes } from './app/routes';
import { reloadAt } from './session/navigation';
import { Session } from './session/session';
import { browserTabChannel } from './session/tabs';

const session: Session = new Session({
  whoIsLoggedIn: () => api.whoIsLoggedIn(),
  logOut: () => api.logOut(),
  keepAlive: () => api.GET('/api/auth/me'),
  channel: browserTabChannel(),
  reloadAt,
  currentPath: () => window.location.pathname + window.location.search,
  now: () => Date.now(),
});
const api = createApi({ onRequest: session.requestSent, onSessionLost: session.sessionLost });
const app = { api, session };
const router = createBrowserRouter(createRoutes(app));
const root = document.getElementById('root');

if (!root)
{
  throw new Error('The page has no #root element.');
}

createRoot(root).render(
  <StrictMode>
    <AppContext value={app}>
      <RouterProvider router={router} />
    </AppContext>
  </StrictMode>,
);
