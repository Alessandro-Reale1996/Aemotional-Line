import { type FormEvent, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import { useLoaderData, useNavigate } from 'react-router';

import { ask, type Limits } from '../api/client';
import { useApp } from '../app/context';
import { ErrorText } from '../components/ErrorText';
import { useCommand } from '../components/useCommand';
import { useUnsentText } from '../session/unsent';
import { ERROR_TEXTS } from '../texts/errors';

export const INVITATION_HINT = 'Ognuna delle due persone riceverà un’email; chi non ha ancora un account ne riceverà anche una per attivarlo.';
export const INVITATION_SENT = 'Invito inviato.';

const PEOPLE = [
  { key: 'One', ordinal: 'prima' },
  { key: 'Two', ordinal: 'seconda' },
] as const;

type Fields = { nameOne: string; emailOne: string; nameTwo: string; emailTwo: string };

/** What the app can say before sending (CLAUDE.md, frontend point 4): a missing field, by name, and the same email twice. */
export function invitationProblem(fields: Fields): string | undefined
{
  for (const { key, ordinal } of PEOPLE)
  {
    if (fields[`name${key}`].trim() === '')
    {
      return `Manca il nome della ${ordinal} persona.`;
    }

    if (fields[`email${key}`].trim() === '')
    {
      return `Manca l’email della ${ordinal} persona.`;
    }
  }

  return fields.emailOne.trim().toLowerCase() === fields.emailTwo.trim().toLowerCase() ? ERROR_TEXTS.SAME_EMAIL_TWICE : undefined;
}

/**
 * /inviti/nuovo (CLAUDE.md, frontend point 5): the therapist invites two people by name and email. The fields hold text
 * not sent yet, protected like any other (frontend point 7), and the browser doesn't remember them (autocomplete off).
 */
export function NewInvitationPage()
{
  const { api } = useApp();
  const limits = useLoaderData() as Limits;
  const navigate = useNavigate();
  const [fields, setFields] = useState<Fields>({ nameOne: '', emailOne: '', nameTwo: '', emailTwo: '' });
  const release = useUnsentText(Object.values(fields).some((value) => value !== ''));
  const invite = useCommand(() => ask(api.POST('/api/invitations', { body: fields })), {
    reread: false,
    onDone: () =>
    {
      release();
      void navigate('/', { state: { notice: INVITATION_SENT } });
    },
  });

  const submit = (event: FormEvent) =>
  {
    event.preventDefault();
    const problem = invitationProblem(fields);

    if (problem)
    {
      invite.setError(problem);
    }
    else
    {
      void invite.run();
    }
  };

  return (
    <>
      <h1 className="h4 mb-3">Invita una coppia</h1>
      <p>{INVITATION_HINT}</p>
      <Form noValidate onSubmit={submit}>
        {PEOPLE.map(({ key, ordinal }) => (
          <fieldset key={key} className="mb-3">
            <legend className="h6">{ordinal === 'prima' ? 'Prima persona' : 'Seconda persona'}</legend>
            <Row className="g-2">
              <Col sm={6}>
                <Form.Group controlId={`name${key}`}>
                  <Form.Label>Nome</Form.Label>
                  <Form.Control
                    value={fields[`name${key}`]}
                    onChange={(event) => setFields((was) => ({ ...was, [`name${key}`]: event.target.value }))}
                    maxLength={limits.displayName}
                    autoComplete="off"
                    disabled={invite.pending}
                  />
                </Form.Group>
              </Col>
              <Col sm={6}>
                <Form.Group controlId={`email${key}`}>
                  <Form.Label>Email</Form.Label>
                  <Form.Control
                    type="email"
                    value={fields[`email${key}`]}
                    onChange={(event) => setFields((was) => ({ ...was, [`email${key}`]: event.target.value }))}
                    maxLength={limits.email}
                    autoComplete="off"
                    autoCapitalize="off"
                    spellCheck={false}
                    disabled={invite.pending}
                  />
                </Form.Group>
              </Col>
            </Row>
          </fieldset>
        ))}
        <Button type="submit" disabled={invite.pending}>
          Invia l’invito
        </Button>
        <ErrorText error={invite.error} />
      </Form>
    </>
  );
}
