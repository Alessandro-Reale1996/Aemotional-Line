import { useState } from 'react';
import Button from 'react-bootstrap/Button';
import { useLoaderData } from 'react-router';

import { ask, type Couple } from '../api/client';
import { useAccount, useApp } from '../app/context';
import { ErrorText } from '../components/ErrorText';
import { PasswordConfirmDialog } from '../components/PasswordConfirmDialog';
import { useCommand } from '../components/useCommand';
import { ERROR_TEXTS } from '../texts/errors';
import { ROLE_LABELS } from '../texts/labels';

export interface AccountData
{
  couples: Couple[];
}

export const PASSWORD_TEXT = 'Per cambiarla ti mandiamo un link al tuo indirizzo email: usandolo, tutte le sessioni aperte si chiuderanno.';
export const PASSWORD_LINK_SENT = 'Ti abbiamo mandato il link: vale un’ora.';
export const DELETE_ACCOUNT_TEXT =
  'Non potrai più accedere. I messaggi scambiati nei percorsi conclusi restano nell’archivio della coppia finché chi vi seguiva in terapia non ne elimina i dati.';

/**
 * /account (CLAUDE.md, frontend point 5 and F5): name, email and role; a new password through the emailed link, the API's
 * only way; a patient outside an active couple deletes the account with the password.
 */
export function AccountPage()
{
  const me = useAccount();
  const { couples } = useLoaderData() as AccountData;
  const { api, session } = useApp();
  const [linkSent, setLinkSent] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const sendLink = useCommand(() => ask(api.POST('/api/auth/password-reset', { body: { email: me?.email ?? '' } })), {
    reread: false,
    onDone: () => setLinkSent(true),
  });

  if (!me)
  {
    return null;
  }

  return (
    <>
      <h1 className="h4 mb-4">Il mio account</h1>
      <dl className="row">
        <dt className="col-sm-3">Nome</dt>
        <dd className="col-sm-9">{me.displayName}</dd>
        <dt className="col-sm-3">Email</dt>
        <dd className="col-sm-9">{me.email}</dd>
        <dt className="col-sm-3">Ruolo</dt>
        <dd className="col-sm-9">{ROLE_LABELS[me.role]}</dd>
      </dl>
      <section className="mb-4">
        <h2 className="h5">Password</h2>
        <p>{PASSWORD_TEXT}</p>
        {linkSent ? (
          <p className="text-success">{PASSWORD_LINK_SENT}</p>
        ) : (
          <Button variant="outline-primary" onClick={() => void sendLink.run()} disabled={sendLink.pending}>
            Inviami il link
          </Button>
        )}
        <ErrorText error={sendLink.error} />
      </section>
      {me.role === 'PATIENT' && (
        <section>
          <h2 className="h5">Elimina l’account</h2>
          {couples.some((couple) => couple.active) ? (
            <p>{ERROR_TEXTS.ACCOUNT_IN_ACTIVE_COUPLE}</p>
          ) : (
            <>
              <p>{DELETE_ACCOUNT_TEXT}</p>
              <Button variant="outline-danger" onClick={() => setConfirming(true)}>
                Elimina l’account
              </Button>
            </>
          )}
          <PasswordConfirmDialog
            show={confirming}
            title="Eliminare l’account?"
            text={DELETE_ACCOUNT_TEXT}
            confirm="Elimina l’account"
            reread={false}
            action={(password) => ask(api.POST('/api/me/account-deletion', { body: { password } }))}
            onDone={() => session.accountDeleted()}
            onCancel={() => setConfirming(false)}
          />
        </section>
      )}
    </>
  );
}
