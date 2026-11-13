import { type FormEvent, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';

import { type Account, ask } from '../api/client';
import { useApp } from '../app/context';
import { ErrorText } from '../components/ErrorText';
import { PasswordField } from '../components/PasswordField';
import { useCommand } from '../components/useCommand';
import { MISSING_EMAIL, MISSING_PASSWORD } from './passwords';

interface Props
{
  initialEmail?: string;
  onLoggedIn: (account: Account) => void;
}

/**
 * Email and password, on the login page and over a page whose session was lost. Compatible with password managers
 * (frontend point 7); every failure reads the same, so it reveals nothing about the account (frontend point 4).
 */
export function LoginForm({ initialEmail = '', onLoggedIn }: Props)
{
  const { api } = useApp();
  const [email, setEmail] = useState(initialEmail);
  const [password, setPassword] = useState('');
  const login = useCommand(() => ask(api.POST('/api/auth/login', { body: { email, password } })), { onDone: onLoggedIn, reread: false });

  const submit = (event: FormEvent) =>
  {
    event.preventDefault();

    if (email.trim() === '')
    {
      login.setError(MISSING_EMAIL);
    }
    else if (password === '')
    {
      login.setError(MISSING_PASSWORD);
    }
    else
    {
      void login.run();
    }
  };

  return (
    <Form noValidate onSubmit={submit}>
      <Form.Group className="mb-3" controlId="login-email">
        <Form.Label>Email</Form.Label>
        <Form.Control
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          autoComplete="username"
          autoCapitalize="off"
          spellCheck={false}
          disabled={login.pending}
        />
      </Form.Group>
      <PasswordField id="login-password" label="Password" value={password} onChange={setPassword} autoComplete="current-password" disabled={login.pending} />
      <Button type="submit" disabled={login.pending}>
        Accedi
      </Button>
      <ErrorText error={login.error} />
    </Form>
  );
}
