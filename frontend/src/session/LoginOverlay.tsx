import { useState } from 'react';
import Button from 'react-bootstrap/Button';
import Modal from 'react-bootstrap/Modal';

import type { Account } from '../api/client';
import { useApp } from '../app/context';
import { LoginForm } from '../auth/LoginForm';
import { ErrorText } from '../components/ErrorText';
import { useCommand } from '../components/useCommand';
import { EXIT_WITH_TEXT, OVERLAY_NOTICE } from './notices';

/**
 * The login over the hidden page (CLAUDE.md, frontend point 7): shown when the session is lost while a form holds unsent
 * text. The same account comes back where it was, with the text; another account, or "Esci", leaves the page and the text
 * with it, after saying so.
 */
export function LoginOverlay({ account }: { account: Account })
{
  const { session } = useApp();
  const [confirmingExit, setConfirmingExit] = useState(false);
  const exit = useCommand(() => session.logOut(), { reread: false });

  return (
    <Modal show backdrop="static" keyboard={false} centered aria-labelledby="overlay-title">
      <Modal.Header>
        <Modal.Title id="overlay-title" className="h5">
          Accedi di nuovo
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <p>{OVERLAY_NOTICE}</p>
        <LoginForm initialEmail={account.email} onLoggedIn={(again) => session.loggedInAgain(again)} />
      </Modal.Body>
      <Modal.Footer className="justify-content-start">
        {confirmingExit ? (
          <>
            <p className="w-100 mb-2">{EXIT_WITH_TEXT}</p>
            <Button variant="secondary" onClick={() => setConfirmingExit(false)}>
              Annulla
            </Button>
            <Button variant="danger" onClick={() => void exit.run()} disabled={exit.pending}>
              Esci comunque
            </Button>
          </>
        ) : (
          <Button variant="outline-secondary" onClick={() => setConfirmingExit(true)}>
            Esci
          </Button>
        )}
        <ErrorText error={exit.error} />
      </Modal.Footer>
    </Modal>
  );
}
