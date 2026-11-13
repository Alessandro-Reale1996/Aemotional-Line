import { type FormEvent, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import Modal from 'react-bootstrap/Modal';

import { MISSING_PASSWORD } from '../auth/passwords';
import { ErrorText } from './ErrorText';
import { PasswordField } from './PasswordField';
import { useCommand } from './useCommand';

interface Props
{
  show: boolean;
  title: string;
  /** What will happen, and that it can't be undone. */
  text: string;
  confirm: string;
  /** The irreversible action, with the password that confirms it. */
  action: (password: string) => Promise<unknown>;
  onDone?: () => void;
  onCancel: () => void;
  /** Re-read the page after the action (true by default; false when the page goes away). */
  reread?: boolean;
}

/**
 * The confirmation of an irreversible action outside the normal flow (CLAUDE.md, R4 point 5 and F5): what will happen,
 * then the account's password. A wrong password counts as a failed login on the server.
 */
export function PasswordConfirmDialog({ show, title, text, confirm, action, onDone, onCancel, reread = true }: Props)
{
  const [password, setPassword] = useState('');
  const command = useCommand(() => action(password), {
    reread,
    onDone: () =>
    {
      setPassword('');
      onDone?.();
    },
  });

  const submit = (event: FormEvent) =>
  {
    event.preventDefault();

    if (password === '')
    {
      command.setError(MISSING_PASSWORD);
    }
    else
    {
      void command.run();
    }
  };

  const cancel = () =>
  {
    setPassword('');
    command.setError(undefined);
    onCancel();
  };

  return (
    <Modal show={show} onHide={cancel} centered aria-labelledby="password-confirm-title">
      <Form noValidate onSubmit={submit}>
        <Modal.Header closeButton closeLabel="Chiudi">
          <Modal.Title id="password-confirm-title" className="h5">
            {title}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <p>{text}</p>
          <PasswordField
            id="confirm-password"
            label="La tua password"
            value={password}
            onChange={setPassword}
            autoComplete="current-password"
            disabled={command.pending}
          />
          <ErrorText error={command.error} />
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={cancel}>
            Annulla
          </Button>
          <Button type="submit" variant="danger" disabled={command.pending}>
            {confirm}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  );
}
