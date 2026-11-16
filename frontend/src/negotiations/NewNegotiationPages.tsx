import { type FormEvent, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import { Link, useLoaderData, useLocation, useNavigate } from 'react-router';

import { ask, type Couple, type Limits } from '../api/client';
import { useApp } from '../app/context';
import { ErrorText } from '../components/ErrorText';
import { TextField } from '../components/TextField';
import { useCommand } from '../components/useCommand';
import { useUnsentText } from '../session/unsent';

export interface NewNegotiationData
{
  couple: Couple;
  limits: Limits;
}

export const AGREEMENT_HINT =
  'L’altra persona riceverà la richiesta e potrà accettare di discuterne o rifiutarla. Se accetta, potrai rivedere il testo e inviarlo con una motivazione.';
export const DISCUSSION_HINT =
  'Un discorso specifico approfondisce un argomento a parte. L’altra persona potrà accettare di discuterne o rifiutare; se accetta, titolo e sottotitolo si concordano come per l’accordo.';
export const REQUEST_SENT = 'Richiesta inviata.';
export const FROM_A_PARAGRAPH = 'Nasce da un paragrafo della conversazione.';

/** After a request is sent: its stipula, with a notice. */
function useGoToStipula(release: () => void)
{
  const navigate = useNavigate();

  return ({ id }: { id: string }) =>
  {
    release();
    void navigate(`/stipule/${id}`, { state: { notice: REQUEST_SENT } });
  };
}

/**
 * /coppie/:id/nuovo-accordo (CLAUDE.md, F6): a partner asks to stipulate the agreement, or to change the one in force; the
 * text is the request the other partner will accept to discuss, or refuse.
 */
export function NewAgreementPage()
{
  const { couple, limits } = useLoaderData() as NewNegotiationData;
  const { api } = useApp();
  const [text, setText] = useState('');
  const release = useUnsentText(text !== '');
  const send = useCommand(() => ask(api.POST('/api/couples/{id}/agreement-negotiations', { params: { path: { id: couple.id } }, body: { text } })), {
    reread: false,
    onDone: useGoToStipula(release),
  });

  const submit = (event: FormEvent) =>
  {
    event.preventDefault();

    if (text.trim() === '')
    {
      send.setError('Manca il testo dell’accordo.');
    }
    else
    {
      void send.run();
    }
  };

  return (
    <>
      <p className="mb-2">
        <Link to={`/coppie/${couple.id}`}>Torna alla coppia</Link>
      </p>
      <h1 className="h4 mb-3">{couple.conversation ? 'Proponi una modifica dell’accordo' : 'Proponi l’accordo'}</h1>
      <p>{AGREEMENT_HINT}</p>
      <Form noValidate onSubmit={submit}>
        <TextField id="agreement-text" label="Testo dell’accordo" value={text} onChange={setText} maxLength={limits.agreementText} rows={10}
          disabled={send.pending} />
        <Button type="submit" disabled={send.pending}>
          Invia la richiesta
        </Button>
        <ErrorText error={send.error} />
      </Form>
    </>
  );
}

/**
 * /conversazioni/:id/nuovo-discorso (CLAUDE.md, F6): a partner asks to open a specific discussion, with its title and
 * subtitle; `?paragrafo=<id>` when it expands a paragraph (F8's "Approfondisci").
 */
export function NewDiscussionPage()
{
  const { couple, limits } = useLoaderData() as NewNegotiationData;
  const { api } = useApp();
  const origin = new URLSearchParams(useLocation().search).get('paragrafo');
  const [title, setTitle] = useState('');
  const [subtitle, setSubtitle] = useState('');
  const release = useUnsentText(title !== '' || subtitle !== '');
  const send = useCommand(
    () => ask(api.POST('/api/conversations/{id}/discussion-negotiations', {
      params: { path: { id: couple.conversation ?? '' } },
      body: { title, subtitle, originParagraphId: origin },
    })),
    { reread: false, onDone: useGoToStipula(release) },
  );

  const submit = (event: FormEvent) =>
  {
    event.preventDefault();

    if (title.trim() === '')
    {
      send.setError('Manca il titolo.');
    }
    else if (subtitle.trim() === '')
    {
      send.setError('Manca il sottotitolo.');
    }
    else
    {
      void send.run();
    }
  };

  return (
    <>
      <p className="mb-2">
        <Link to={`/coppie/${couple.id}`}>Torna alla coppia</Link>
      </p>
      <h1 className="h4 mb-3">Proponi un discorso specifico</h1>
      <p>{DISCUSSION_HINT}</p>
      {origin && <p className="text-body-secondary">{FROM_A_PARAGRAPH}</p>}
      <Form noValidate onSubmit={submit}>
        <TextField id="discussion-title" label="Titolo" value={title} onChange={setTitle} maxLength={limits.discussionTitle} disabled={send.pending} />
        <TextField id="discussion-subtitle" label="Sottotitolo" value={subtitle} onChange={setSubtitle} maxLength={limits.discussionSubtitle} rows={2}
          disabled={send.pending} />
        <Button type="submit" disabled={send.pending}>
          Invia la richiesta
        </Button>
        <ErrorText error={send.error} />
      </Form>
    </>
  );
}
