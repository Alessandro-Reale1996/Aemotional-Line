import { type FormEvent, useEffect, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import { Link, useLoaderData, useLocation, useNavigate } from 'react-router';

import { type Api, ask, type Limits } from '../api/client';
import { ApiError } from '../api/errors';
import { useAccount, useApp } from '../app/context';
import { ErrorText } from '../components/ErrorText';
import { ButtonLink, PublicFrame } from '../components/frames';
import { PasswordField } from '../components/PasswordField';
import { useCommand } from '../components/useCommand';
import { newPasswordProblem, passwordRule } from './passwords';

type Kind = 'activation' | 'reset';

const TEXTS: Record<Kind, { title: string; intro: string; submit: string; done: string; invalidLink: string }> = {
  activation: {
    title: 'Attiva l’account',
    intro: 'Scegli la password del tuo account.',
    submit: 'Attiva l’account',
    done: 'L’account è attivo. Ora puoi accedere.',
    invalidLink: 'Chiedi un nuovo link a chi ti ha invitato.',
  },
  reset: {
    title: 'Nuova password',
    intro: 'Scegli una nuova password.',
    submit: 'Salva la nuova password',
    done: 'La password è cambiata e tutte le sessioni aperte sono state chiuse. Ora puoi accedere.',
    invalidLink: 'Puoi chiederne uno nuovo da «Password dimenticata».',
  },
};

export const NO_CODE = 'Per continuare, apri il link che hai ricevuto per email.';

function send(api: Api, kind: Kind, code: string, password: string)
{
  return kind === 'activation'
    ? ask(api.POST('/api/auth/activate', { body: { code, password } }))
    : ask(api.POST('/api/auth/password-reset/confirm', { body: { code, password } }));
}

/**
 * /attiva and /reimposta-password, opened from an email's link (CLAUDE.md, frontend notes): the one-time code is read and
 * removed from the address at once, then the new password goes with it. With another account's session open, the page
 * offers to leave it before logging in (F4 decision 4).
 */
export function NewPasswordPage({ kind }: { kind: Kind })
{
  const texts = TEXTS[kind];
  const { api, session } = useApp();
  const account = useAccount();
  const limits = useLoaderData() as Limits;
  const location = useLocation();
  const navigate = useNavigate();
  const [code] = useState(() => new URLSearchParams(location.search).get('codice') ?? '');
  const [password, setPassword] = useState('');
  const [done, setDone] = useState(false);
  const save = useCommand(() => send(api, kind, code, password), { onDone: () => setDone(true), reread: false });
  const exit = useCommand(() => session.logOut(), { reread: false });
  const linkRefused = save.error instanceof ApiError && save.error.code === 'INVALID_LINK';

  useEffect(() =>
  {
    if (new URLSearchParams(location.search).has('codice'))
    {
      void navigate(location.pathname, { replace: true });
    }
  }, [location.pathname, location.search, navigate]);

  const submit = (event: FormEvent) =>
  {
    event.preventDefault();
    const problem = newPasswordProblem(password, limits);

    if (problem)
    {
      save.setError(problem);
    }
    else
    {
      void save.run();
    }
  };

  let content;

  if (done)
  {
    content = (
      <>
        <Alert variant="success">{texts.done}</Alert>
        {account ? (
          <>
            <p>Su questo dispositivo è aperto l’account di «{account.displayName}».</p>
            <Button onClick={() => void exit.run()} disabled={exit.pending}>
              Esci e accedi
            </Button>
            <ErrorText error={exit.error} />
          </>
        ) : (
          <ButtonLink to="/accedi">Accedi</ButtonLink>
        )}
      </>
    );
  }
  else if (code === '')
  {
    content = <p>{NO_CODE}</p>;
  }
  else if (linkRefused)
  {
    content = <ErrorText error={save.error} hint={texts.invalidLink} />;
  }
  else
  {
    content = (
      <Form noValidate onSubmit={submit}>
        <p>{texts.intro}</p>
        <PasswordField
          id="new-password"
          label="Password"
          value={password}
          onChange={setPassword}
          autoComplete="new-password"
          rule={passwordRule(limits)}
          disabled={save.pending}
        />
        <Button type="submit" disabled={save.pending}>
          {texts.submit}
        </Button>
        <ErrorText error={save.error} />
      </Form>
    );
  }

  return (
    <PublicFrame title={texts.title}>
      {content}
      {kind === 'reset' && !done && (
        <p className="mt-4 mb-0">
          <Link to="/password-dimenticata">Password dimenticata</Link>
        </p>
      )}
    </PublicFrame>
  );
}
