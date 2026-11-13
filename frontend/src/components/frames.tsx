import { type ReactNode, useState } from 'react';
import Col from 'react-bootstrap/Col';
import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import NavDropdown from 'react-bootstrap/NavDropdown';
import Row from 'react-bootstrap/Row';
import { Link, useBlocker } from 'react-router';

import type { Account } from '../api/client';
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

/**
 * The frame of the pages of a logged-in user (frontend point 5, F4 decision 7): the bar, with the brand and the menu with
 * the name and "Esci"; the links by role come with their pages. While the session is lost and a form holds unsent text,
 * everything is hidden, still mounted, under the login (frontend point 7). Leaving the page or the session with unsent text
 * asks for confirmation first.
 */
export function AppFrame({ account, children }: { account: Account; children: ReactNode })
{
  const { session } = useApp();
  const locked = useLock();
  const [confirmingExit, setConfirmingExit] = useState(false);
  const exit = useCommand(() => session.logOut(), { reread: false });
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
              <Nav className="ms-auto">
                <NavDropdown title={account.displayName} id="account-menu" align="end">
                  <NavDropdown.Header>{ROLE_LABELS[account.role]}</NavDropdown.Header>
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
