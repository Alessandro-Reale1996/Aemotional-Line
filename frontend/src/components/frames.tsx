import { type ReactNode, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Col from 'react-bootstrap/Col';
import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import NavDropdown from 'react-bootstrap/NavDropdown';
import Row from 'react-bootstrap/Row';
import { Link, NavLink, useBlocker, useLocation, useNavigate } from 'react-router';

import type { Account, Role } from '../api/client';
import { useApp, useLock } from '../app/context';
import { LoginOverlay } from '../session/LoginOverlay';
import { EXIT_WITH_TEXT, LEAVE_WITH_TEXT } from '../session/notices';
import { hasUnsentText } from '../session/unsent';
import { ROLE_LABELS } from '../texts/labels';
import { ConfirmDialog } from './ConfirmDialog';
import { ErrorText } from './ErrorText';
import { useCommand } from './useCommand';

/** The frame of the pages without a session: one narrow column, phone first (frontend point 5). */
export function PublicFrame({ title, children }: { title: string; children: ReactNode })
{
  return (
    <Container as="main" className="py-5">
      <Row className="justify-content-center">
        <Col sm={10} md={8} lg={6} xl={5}>
          <p className="h5 mb-4 text-body-secondary">Aemotional-Line</p>
          <h1 className="h3 mb-4">{title}</h1>
          {children}
        </Col>
      </Row>
    </Container>
  );
}

/** The bar's links by role (frontend point 5): each step adds its own when its page exists. */
const LINKS: Record<Role, { to: string; label: string }[]> = {
  PATIENT: [{ to: '/', label: 'Da fare' }],
  THERAPIST: [
    { to: '/', label: 'Coppie' },
    { to: '/inviti/nuovo', label: 'Invita' },
  ],
  ADMINISTRATOR: [{ to: '/amministrazione', label: 'Terapeuti' }],
};

/**
 * The frame of the pages of a logged-in user (frontend point 5, F4 decision 7): the bar, with the brand, the links of the
 * user's role and the menu with the name, "Il mio account" and "Esci". While the session is lost and a form holds unsent text,
 * everything is hidden, still mounted, under the login (frontend point 7). Leaving the page or the session with unsent text
 * asks for confirmation first.
 */
export function AppFrame({ account, children }: { account: Account; children: ReactNode })
{
  const { session } = useApp();
  const locked = useLock();
  const [confirmingExit, setConfirmingExit] = useState(false);
  const exit = useCommand(() => session.logOut(), { reread: false });
  const navigate = useNavigate();
  const blocker = useBlocker(({ currentLocation, nextLocation }) =>
    hasUnsentText() && currentLocation.pathname !== nextLocation.pathname);

  const askToExit = () =>
  {
    if (hasUnsentText())
    {
      setConfirmingExit(true);
    }
    else
    {
      void exit.run();
    }
  };

  return (
    <>
      <div hidden={locked !== null}>
        <Navbar expand="sm" className="bg-body-tertiary border-bottom">
          <Container>
            <Link className="navbar-brand" to="/">
              Aemotional-Line
            </Link>
            <Navbar.Toggle aria-controls="main-menu" label="Menu" />
            <Navbar.Collapse id="main-menu">
              <Nav className="me-auto">
                {LINKS[account.role].map((link) => (
                  <NavLink key={link.to} to={link.to} end className="nav-link">
                    {link.label}
                  </NavLink>
                ))}
              </Nav>
              <Nav>
                <NavDropdown title={account.displayName} id="account-menu" align="end">
                  <NavDropdown.Header>{ROLE_LABELS[account.role]}</NavDropdown.Header>
                  <NavDropdown.Item as="button" onClick={() => void navigate('/account')}>
                    Il mio account
                  </NavDropdown.Item>
                  <NavDropdown.Divider />
                  <NavDropdown.Item as="button" onClick={askToExit} disabled={exit.pending}>
                    Esci
                  </NavDropdown.Item>
                </NavDropdown>
              </Nav>
            </Navbar.Collapse>
          </Container>
        </Navbar>
        <Container as="main" className="py-4">
          <ErrorText error={exit.error} />
          {children}
        </Container>
      </div>
      {locked !== null && <LoginOverlay account={account} />}
      <ConfirmDialog
        show={confirmingExit}
        text={EXIT_WITH_TEXT}
        confirm="Esci comunque"
        cancel="Annulla"
        onConfirm={() =>
        {
          setConfirmingExit(false);
          void exit.run();
        }}
        onCancel={() => setConfirmingExit(false)}
      />
      <ConfirmDialog
        show={blocker.state === 'blocked'}
        text={LEAVE_WITH_TEXT}
        confirm="Lascia la pagina"
        cancel="Resta"
        onConfirm={() => blocker.proceed?.()}
        onCancel={() => blocker.reset?.()}
      />
    </>
  );
}

/** A notice left by the page before, after an action that made it go away (F5 decision 3). */
export function useNotice(): string | undefined
{
  const state = useLocation().state as { notice?: unknown } | null;

  return typeof state?.notice === 'string' ? state.notice : undefined;
}

export function Notice()
{
  const notice = useNotice();

  return notice ? <Alert variant="success">{notice}</Alert> : null;
}

export function Loading()
{
  return (
    <Container as="main" className="py-5">
      <p className="text-body-secondary">Caricamento…</p>
    </Container>
  );
}

/** A link styled as a button (React-Bootstrap's `as` would lose React Router's types). */
export function ButtonLink({ to, children, variant = 'primary' }: { to: string; children: ReactNode; variant?: string })
{
  return (
    <Link className={`btn btn-${variant}`} to={to}>
      {children}
    </Link>
  );
}
