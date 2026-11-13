import { type FormEvent, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import { useLoaderData } from 'react-router';

import { ask, type Limits, type Therapist } from '../api/client';
import { useApp } from '../app/context';
import { ErrorText } from '../components/ErrorText';
import { ButtonLink } from '../components/frames';
import { PasswordConfirmDialog } from '../components/PasswordConfirmDialog';
import { useCommand } from '../components/useCommand';
import { useUnsentText } from '../session/unsent';
import { ACCOUNT_STATUS_LABELS } from '../texts/labels';

export interface AdministrationData
{
  therapists: Therapist[];
  limits: Limits;
}

export const NEW_THERAPIST_HINT = 'All’indirizzo indicato arriverà un’email per scegliere la password.';
export const LINK_SENT = 'Link inviato.';

export function disableText(name: string): string
{
  return `Disattivando l’account di «${name}» si concludono i suoi percorsi in corso e si ritirano i suoi inviti; le sue sessioni aperte si chiudono. Non si può annullare.`;
}

/**
 * /amministrazione (CLAUDE.md, frontend point 5 and F5): the therapists' accounts. A pending one can get a new activation
 * link, an active one can be disabled with the administrator's password, a disabled one's ended couples have a page.
 */
export function AdministrationPage()
{
  const { therapists, limits } = useLoaderData() as AdministrationData;

  return (
    <>
      <h1 className="h4 mb-4">Terapeuti</h1>
      <NewTherapist limits={limits} />
      <section>
        {therapists.length === 0 && <p className="text-body-secondary">Nessun terapeuta.</p>}
        {therapists.map((therapist) => (
          <TherapistCard key={therapist.id} therapist={therapist} />
        ))}
      </section>
    </>
  );
}

function NewTherapist({ limits }: { limits: Limits })
{
  const { api } = useApp();
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const release = useUnsentText(displayName !== '' || email !== '');
  const create = useCommand(() => ask(api.POST('/api/admin/therapists', { body: { displayName, email } })), {
    onDone: () =>
    {
      release();
      setDisplayName('');
      setEmail('');
    },
  });

  const submit = (event: FormEvent) =>
  {
    event.preventDefault();

    if (displayName.trim() === '')
    {
      create.setError('Manca il nome.');
    }
    else if (email.trim() === '')
    {
      create.setError('Manca l’email.');
    }
    else
    {
      void create.run();
    }
  };

  return (
    <Card body className="mb-4">
      <h2 className="h5">Nuovo terapeuta</h2>
      <Form noValidate onSubmit={submit}>
        <Row className="g-2 mb-2">
          <Col sm={6}>
            <Form.Group controlId="therapist-name">
              <Form.Label>Nome</Form.Label>
              <Form.Control value={displayName} onChange={(event) => setDisplayName(event.target.value)} maxLength={limits.displayName}
                autoComplete="off" disabled={create.pending} />
            </Form.Group>
          </Col>
          <Col sm={6}>
            <Form.Group controlId="therapist-email">
              <Form.Label>Email</Form.Label>
              <Form.Control type="email" value={email} onChange={(event) => setEmail(event.target.value)} maxLength={limits.email}
                autoComplete="off" autoCapitalize="off" spellCheck={false} disabled={create.pending} />
            </Form.Group>
          </Col>
        </Row>
        <p className="text-body-secondary small">{NEW_THERAPIST_HINT}</p>
        <Button type="submit" disabled={create.pending}>
          Crea l’account
        </Button>
        <ErrorText error={create.error} />
      </Form>
    </Card>
  );
}

function TherapistCard({ therapist }: { therapist: Therapist })
{
  const { api } = useApp();
  const [confirming, setConfirming] = useState(false);
  const [linkSent, setLinkSent] = useState(false);
  const path = { params: { path: { id: therapist.id } } };
  const resend = useCommand(() => ask(api.POST('/api/admin/therapists/{id}/activation-link', path)), { onDone: () => setLinkSent(true) });

  return (
    <Card body className="mb-2">
      <div className="fw-semibold">{therapist.displayName}</div>
      <div className="text-body-secondary small mb-2">
        {therapist.email} · {ACCOUNT_STATUS_LABELS[therapist.status]}
      </div>
      {therapist.status === 'PENDING' && (
        <>
          <Button variant="outline-primary" size="sm" onClick={() => void resend.run()} disabled={resend.pending}>
            Invia di nuovo il link
          </Button>
          {linkSent && <span className="ms-2 text-success">{LINK_SENT}</span>}
          <ErrorText error={resend.error} />
        </>
      )}
      {therapist.status === 'ACTIVE' && (
        <Button variant="outline-danger" size="sm" onClick={() => setConfirming(true)}>
          Disattiva
        </Button>
      )}
      {therapist.status === 'DISABLED' && (
        <ButtonLink to={`/amministrazione/terapeuti/${therapist.id}`} variant="outline-secondary btn-sm">
          Percorsi conclusi
        </ButtonLink>
      )}
      <PasswordConfirmDialog
        show={confirming}
        title="Disattivare l’account?"
        text={disableText(therapist.displayName)}
        confirm="Disattiva"
        action={(password) => ask(api.POST('/api/admin/therapists/{id}/disable', { ...path, body: { password } }))}
        onDone={() => setConfirming(false)}
        onCancel={() => setConfirming(false)}
      />
    </Card>
  );
}
