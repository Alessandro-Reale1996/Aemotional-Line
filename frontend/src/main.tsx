import 'bootstrap/dist/css/bootstrap.min.css';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { createApi } from './api/client';
import { Provisional } from './Provisional';

const api = createApi();
// Every address shows the stand-in page for now; F4 brings the app's routes.
const router = createBrowserRouter([{ path: '*', element: <Provisional api={api} /> }]);
const root = document.getElementById('root');

if (!root)
{
  throw new Error('The page has no #root element.');
}

createRoot(root).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
);
