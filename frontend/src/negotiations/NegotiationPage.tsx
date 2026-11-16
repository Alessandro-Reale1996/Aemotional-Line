import { type FormEvent, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import { Link, useLoaderData, useNavigate } from 'react-router';

import { type Account, ask, type Couple, type Limits, type Negotiation, type Proposal } from '../api/client';
import { useAccount, useApp } from '../app/context';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ErrorText } from '../components/ErrorText';
import { Notice } from '../components/frames';
import { TextField, UserText } from '../components/TextField';
import { useCommand } from '../components/useCommand';
import { useUnsentText } from '../session/unsent';
import { formatDate } from '../texts/format';
import { NEGOTIATION_STATUS_LABELS, PROPOSAL_STATUS_LABELS, PURPOSE_LABELS } from '../texts/labels';
import { FROM_A_PARAGRAPH } from './NewNegotiationPages';
import {
  ABANDON_TEXT,
  ACCEPT_TEXTS,
  ACCEPTED_NOTICES,
  ENDED_WITHOUT_AGREEMENT,
  type Phase,
  phaseOf,
  REFUSE_REQUEST_TEXT,
  WITHDRAW_TEXT,
} from './phase';
import { nameIn, toWhom, who } from './words';

export interface NegotiationData
{
  negotiation: Negotiation;
  couple: Couple;
  limits: Limits;
  /** The paragraph a discussion expands, when the user can see it (F7): its title and where it is. */
  origin?: { title: string; address: string };
}

export const DRAFT_SAVED = 'Bozza salvata.';

/**
 * /stipule/:id (CLAUDE.md, frontend point 6 and F6): the request, the proposals sent in order with the last one
 * highlighted, one line saying where things stand and only the actions possible then. The therapist reads everything
 * but the drafts, which the API shows to their author only.
 */
export function NegotiationPage()
{
  const { negotiation, couple, limits, origin } = useLoaderData() as NegotiationData;
  const me = useAccount();

  if (!me)
  {
    return null;
  }

  const proposals = negotiation.sentProposals;

  return (
    <>
      <p className="mb-2">
        <Link to={`/coppie/${couple.id}`}>Torna alla coppia</Link>
      </p>
      <Notice />
      <h1 className="h4 mb-1">{PURPOSE_LABELS[negotiation.purpose]}</h1>
      <p className="text-body-secondary">
        Richiesta da {who(couple, negotiation.request.author, me)} il {formatDate(negotiation.startedAt)} · {NEGOTIATION_STATUS_LABELS[negotiation.status]}
      </p>
      {negotiation.originParagraphId && (
        <p>
          {origin ? (
            <>
              Nasce da <Link to={origin.address}>«{origin.title}»</Link>
            </>
          ) : (
            FROM_A_PARAGRAPH
          )}
        </p>
      )}
      <section className="mb-4">
        <h2 className="h5">Richiesta</h2>
        <Card body>
          <ProposalContent proposal={negotiation.request} />
        </Card>
      </section>
      <section className="mb-4">
        <h2 className="h5">Proposte</h2>
        {proposals.length === 0 && <p className="text-body-secondary">Nessuna proposta ancora.</p>}
        {proposals.map((proposal, index) => (
          <ProposalCard key={proposal.id} proposal={proposal} couple={couple} me={me} latest={index === proposals.length - 1} />
        ))}
      </section>
      <section className="mb-4">
        <Actions negotiation={negotiation} couple={couple} me={me} phase={phaseOf(negotiation, me)} limits={limits} />
      </section>
    </>
  );
}

/** The text of an agreement, or the title and subtitle of a discussion, as written. */
function ProposalContent({ proposal }: { proposal: Proposal })
{
  if (proposal.kind === 'AGREEMENT')
  {
    return <UserText text={proposal.text ?? ''} className="mb-0" />;
  }

  return (
    <>
      <UserText text={proposal.title ?? ''} className="fw-semibold mb-1" />
      <UserText text={proposal.subtitle ?? ''} className="mb-0" />
    </>
  );
}

function ProposalCard({ proposal, couple, me, latest }: { proposal: Proposal; couple: Couple; me: Account; latest: boolean })
{
  const author = proposal.author === me.id ? 'La tua proposta' : `Proposta di ${nameIn(couple, proposal.author)}`;

  return (
    <Card className={`mb-2 ${latest ? 'border-primary' : ''}`.trim()} aria-current={latest ? 'true' : undefined}>
      <Card.Header className="small">
        {author}
        {proposal.sentAt && ` · ${formatDate(proposal.sentAt)}`} · {PROPOSAL_STATUS_LABELS[proposal.status]}
      </Card.Header>
      <Card.Body>
        <ProposalContent proposal={proposal} />
        {proposal.justification && (
          <>
            <div className="small text-body-secondary mt-3">Motivazione</div>
            <UserText text={proposal.justification} className="mb-0" />
          </>
        )}
      </Card.Body>
    </Card>
  );
}

type Confirming = 'refuse' | 'withdraw' | 'abandon' | 'accept';

/** The line saying where things stand, and the actions possible now (texts approved with F6). */
function Actions({ negotiation, couple, me, phase, limits }: { negotiation: Negotiation; couple: Couple; me: Account; phase: Phase; limits: Limits })
{
  const { api } = useApp();
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState<Confirming>();
  const path = { params: { path: { id: negotiation.id } } };
  const leave = (notice: string) => void navigate(`/coppie/${couple.id}`, { replace: true, state: { notice } });
  const acceptRequest = useCommand(() => ask(api.POST('/api/negotiations/{id}/accept-request', path)));
  const refuseRequest = useCommand(() => ask(api.POST('/api/negotiations/{id}/refuse-request', path)), {
    reread: false,
    onDone: () => leave(ENDED_WITHOUT_AGREEMENT),
  });
  const abandon = useCommand(() => ask(api.POST('/api/negotiations/{id}/abandon', path)), { reread: false, onDone: () => leave(ENDED_WITHOUT_AGREEMENT) });
  // What took effect opens at once (F7): the conversation, or the discussion just opened.
  const acceptProposal = useCommand(() => ask(api.POST('/api/negotiations/{id}/accept-proposal', path)), {
    reread: false,
    onDone: (effect) => void navigate(
      effect.openedDiscussion !== null && negotiation.purpose === 'OPENED_DISCUSSION'
        ? `/conversazioni/${effect.conversationId}/discorsi/${effect.openedDiscussion}`
        : `/conversazioni/${effect.conversationId}`,
      { replace: true, state: { notice: ACCEPTED_NOTICES[negotiation.purpose] } },
    ),
  });
  const refuseProposal = useCommand(() => ask(api.POST('/api/negotiations/{id}/refuse-proposal', path)));
  const commands = [acceptRequest, refuseRequest, abandon, acceptProposal, refuseProposal];
  const busy = commands.some((command) => command.pending);
  const error = commands.find((command) => command.error !== undefined)?.error;
  const isPartner = me.id === couple.partnerOne.id || me.id === couple.partnerTwo.id;
  const lastProposal = negotiation.sentProposals.at(-1);
  const dialogs: Record<Confirming, { title: string; text: string; confirm: string; run: () => Promise<void> }> = {
    refuse: { title: 'Rifiutare la richiesta?', text: REFUSE_REQUEST_TEXT, confirm: 'Rifiuta la richiesta', run: refuseRequest.run },
    withdraw: { title: 'Ritirare la richiesta?', text: WITHDRAW_TEXT, confirm: 'Ritira la richiesta', run: abandon.run },
    abandon: { title: 'Abbandonare la stipula?', text: ABANDON_TEXT, confirm: 'Abbandona la stipula', run: abandon.run },
    accept: { title: 'Accettare la proposta?', text: ACCEPT_TEXTS[negotiation.purpose], confirm: 'Accetta la proposta', run: acceptProposal.run },
  };
  const dialog = confirming ? dialogs[confirming] : undefined;

  let line: string;

  switch (phase)
  {
    case 'answer-request':
      line = `${nameIn(couple, negotiation.request.author)} chiede di avviare questa stipula. Vuoi discuterne?`;
      break;
    case 'await-request':
      line = `In attesa della risposta di ${nameIn(couple, negotiation.currentResponder)}.`;
      break;
    case 'write':
      line = 'Tocca a te: rivedi il testo e invialo con una motivazione.';
      break;
    case 'answer-proposal':
      line = `${nameIn(couple, lastProposal?.author ?? null)} ti ha inviato una proposta.`;
      break;
    case 'await':
      line = `Tocca ${toWhom(nameIn(couple, negotiation.currentResponder))}.`;
      break;
    case 'closed':
      line = `${negotiation.status === 'REFUSED' ? 'Richiesta rifiutata' : 'Abbandonata'} da ${who(couple, negotiation.closedBy, me)}`
        + (negotiation.closedAt ? ` il ${formatDate(negotiation.closedAt)}.` : '.');
      break;
  }

  return (
    <>
      <p className="fw-semibold" role="status">
        {line}
      </p>
      {phase === 'write' && negotiation.draft && (
        <DraftForm key={negotiation.draft.id} negotiationId={negotiation.id} draft={negotiation.draft} limits={limits} />
      )}
      <div className="d-flex flex-wrap gap-2">
        {phase === 'answer-request' && (
          <>
            <Button onClick={() => void acceptRequest.run()} disabled={busy}>
              Accetta la richiesta
            </Button>
            <Button variant="outline-danger" onClick={() => setConfirming('refuse')} disabled={busy}>
              Rifiuta la richiesta
            </Button>
          </>
        )}
        {phase === 'await-request' && negotiation.request.author === me.id && (
          <Button variant="outline-danger" onClick={() => setConfirming('withdraw')} disabled={busy}>
            Ritira la richiesta
          </Button>
        )}
        {phase === 'answer-proposal' && (
          <>
            <Button onClick={() => setConfirming('accept')} disabled={busy}>
              Accetta la proposta
            </Button>
            <Button variant="outline-primary" onClick={() => void refuseProposal.run()} disabled={busy}>
              Rifiuta e proponi una modifica
            </Button>
          </>
        )}
        {isPartner && negotiation.status === 'ACCEPTED' && (
          <Button variant="outline-danger" onClick={() => setConfirming('abandon')} disabled={busy}>
            Abbandona la stipula
          </Button>
        )}
      </div>
      <ErrorText error={error} />
      <ConfirmDialog
        show={dialog !== undefined}
        title={dialog?.title ?? ''}
        text={dialog?.text ?? ''}
        confirm={dialog?.confirm ?? ''}
        cancel="Annulla"
        onConfirm={() =>
        {
          setConfirming(undefined);
          void dialog?.run();
        }}
        onCancel={() => setConfirming(undefined)}
      />
    </>
  );
}

/**
 * This partner's draft (CLAUDE.md, F6 decision 1): "Salva la bozza" keeps the text on the server; the justification
 * reaches it only with "Invia la proposta", so until then it is unsent text, protected like any other.
 */
function DraftForm({ negotiationId, draft, limits }: { negotiationId: string; draft: Proposal; limits: Limits })
{
  const { api } = useApp();
  const agreement = draft.kind === 'AGREEMENT';
  const [stored, setStored] = useState({ text: draft.text ?? '', title: draft.title ?? '', subtitle: draft.subtitle ?? '' });
  const [text, setText] = useState(stored.text);
  const [title, setTitle] = useState(stored.title);
  const [subtitle, setSubtitle] = useState(stored.subtitle);
  const [justification, setJustification] = useState('');
  const [saved, setSaved] = useState(false);
  const changed = agreement ? text !== stored.text : title !== stored.title || subtitle !== stored.subtitle;
  const release = useUnsentText(changed || justification !== '');
  const path = { params: { path: { id: negotiationId } } };
  const saveDraft = () => ask(api.PUT('/api/negotiations/{id}/draft', { ...path, body: agreement ? { text } : { title, subtitle } }));
  const save = useCommand(saveDraft, {
    reread: false,
    onDone: () =>
    {
      setStored({ text, title, subtitle });
      setSaved(true);
    },
  });
  const send = useCommand(async () =>
  {
    if (changed)
    {
      await saveDraft();
    }

    await ask(api.POST('/api/negotiations/{id}/send', { ...path, body: { justification } }));
  }, { onDone: release });

  const contentProblem = () =>
  {
    if (agreement)
    {
      return text.trim() === '' ? 'Manca il testo dell’accordo.' : undefined;
    }

    return title.trim() === '' ? 'Manca il titolo.' : subtitle.trim() === '' ? 'Manca il sottotitolo.' : undefined;
  };

  const edited = (set: (value: string) => void) => (value: string) =>
  {
    set(value);
    setSaved(false);
  };

  const submitSave = () =>
  {
    const problem = contentProblem();

    if (problem)
    {
      save.setError(problem);
    }
    else
    {
      void save.run();
    }
  };

  const submitSend = (event: FormEvent) =>
  {
    event.preventDefault();
    const problem = contentProblem() ?? (justification.trim() === '' ? 'Manca la motivazione.' : undefined);

    if (problem)
    {
      send.setError(problem);
    }
    else
    {
      void send.run();
    }
  };

  const busy = save.pending || send.pending;

  return (
    <Form noValidate onSubmit={submitSend} className="mb-3">
      {agreement ? (
        <TextField id="draft-text" label="Testo dell’accordo" value={text} onChange={edited(setText)} maxLength={limits.agreementText} rows={10}
          disabled={busy} />
      ) : (
        <>
          <TextField id="draft-title" label="Titolo" value={title} onChange={edited(setTitle)} maxLength={limits.discussionTitle} disabled={busy} />
          <TextField id="draft-subtitle" label="Sottotitolo" value={subtitle} onChange={edited(setSubtitle)} maxLength={limits.discussionSubtitle} rows={2}
            disabled={busy} />
        </>
      )}
      <TextField id="justification" label="Motivazione" hint="Spiega perché proponi questo testo." value={justification} onChange={setJustification}
        maxLength={limits.proposalJustification} rows={4} disabled={busy} />
      <div className="d-flex flex-wrap gap-2 align-items-center">
        <Button variant="outline-secondary" onClick={submitSave} disabled={busy}>
          Salva la bozza
        </Button>
        <Button type="submit" disabled={busy}>
          Invia la proposta
        </Button>
        {saved && <span className="text-success">{DRAFT_SAVED}</span>}
      </div>
      <ErrorText error={save.error ?? send.error} />
    </Form>
  );
}
