import { type FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import { Link } from 'react-router';

import { ask } from '../api/client';
import { useApp } from '../app/context';
import { ErrorText } from '../components/ErrorText';
import { PublicFrame } from '../components/frames';
import { useCommand } from '../components/useCommand';
import { MISSING_EMAIL } from './passwords';

export const RESET_REQUESTED =
  'Se l’indirizzo corrisponde a un account attivo, riceverai un’email con un link per scegliere una nuova password. Il link vale un’ora.';

/** /password-dimenticata: the answer is the same whether the email has an account or not (CLAUDE.md, security point 6). */
export function ForgottenPasswordPage()
{
  const { api } = useApp();
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const request = useCommand(() => ask(api.POST('/api/auth/password-reset', { body: { email } })), { onDone: () => setSent(true), reread: false });

  const submit = (event: FormEvent) =>
  {
    event.preventDefault();

    if (email.trim() === '')
    {
      request.setError(MISSING_EMAIL);
    }
    else
    {
      void request.run();
    }
  };

  return (
    <PublicFrame title="Password dimenticata">
      {sent ? (
        <Alert variant="success">{RESET_REQUESTED}</Alert>
      ) : (
        <Form noValidate onSubmit={submit}>
          <p>Scrivi l’indirizzo email del tuo account: riceverai un link per scegliere una nuova password.</p>
          <Form.Group className="mb-3" controlId="reset-email">
            <Form.Label>Email</Form.Label>
            <Form.Control
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="username"
              autoCapitalize="off"
              spellCheck={false}
              disabled={request.pending}
            />
          </Form.Group>
          <Button type="submit" disabled={request.pending}>
            Invia il link
          </Button>
          <ErrorText error={request.error} />
        </Form>
      )}
      <p className="mt-4 mb-0">
        <Link to="/accedi">Torna all’accesso</Link>
      </p>
    </PublicFrame>
  );
}
