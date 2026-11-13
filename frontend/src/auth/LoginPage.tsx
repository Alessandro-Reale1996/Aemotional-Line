import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import { Link, useLocation } from 'react-router';

import type { Account } from '../api/client';
import { useAccount, useApp } from '../app/context';
import { ErrorText } from '../components/ErrorText';
import { ButtonLink, PublicFrame } from '../components/frames';
import { useCommand } from '../components/useCommand';
import type { LoginState } from '../session/navigation';
import { END_NOTICES, SHARED_DEVICE } from '../session/notices';
import { LoginForm } from './LoginForm';

/**
 * /accedi (CLAUDE.md, frontend points 3 and 7): says why it is shown, then goes back where the user was. With a session
 * already open it says whose, and never takes anyone silently into someone else's home (F4 decision 4).
 */
export function LoginPage()
{
  const account = useAccount();
  const { session } = useApp();
  const state = useLocation().state as LoginState | null;

  if (account)
  {
    return <AlreadyLoggedIn account={account} />;
  }

  const notice = state?.reason ? END_NOTICES[state.reason] : undefined;

  return (
    <PublicFrame title="Accedi">
      {notice && <Alert variant={state?.reason === 'logout' ? 'success' : 'info'}>{notice}</Alert>}
      <LoginForm onLoggedIn={() => session.loggedIn(state?.returnTo)} />
      <p className="mt-4 mb-2">
        <Link to="/password-dimenticata">Password dimenticata?</Link>
      </p>
      <p className="text-body-secondary small mb-0">{SHARED_DEVICE}</p>
    </PublicFrame>
  );
}

function AlreadyLoggedIn({ account }: { account: Account })
{
  const { session } = useApp();
  const exit = useCommand(() => session.logOut(), { reread: false });

  return (
    <PublicFrame title="Accedi">
      <p>Hai già effettuato l’accesso con l’account di «{account.displayName}».</p>
      <div className="d-flex flex-wrap gap-2">
        <ButtonLink to="/">Vai alla home</ButtonLink>
        <Button variant="outline-secondary" onClick={() => void exit.run()} disabled={exit.pending}>
          Esci
        </Button>
      </div>
      <ErrorText error={exit.error} />
    </PublicFrame>
  );
}
