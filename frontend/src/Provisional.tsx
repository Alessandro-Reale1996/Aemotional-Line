import { useEffect, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Container from 'react-bootstrap/Container';
import Dropdown from 'react-bootstrap/Dropdown';
import Modal from 'react-bootstrap/Modal';

import type { Api, Limits } from './api/client';

/**
 * A stand-in page for F3 only (CLAUDE.md): it shows that the strict CSP leaves React-Bootstrap working (a dropdown placed
 * by Popper, a modal with its transitions and Bootstrap's embedded icons) and that the typed client reaches the API. F4
 * replaces it with the app's pages.
 */
export function Provisional({ api }: { api: Api })
{
  const [limits, setLimits] = useState<Limits>();
  const [unreachable, setUnreachable] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() =>
  {
    let current = true;

    api.GET('/api/limits').then(
      ({ data }) =>
      {
        if (current)
        {
          if (data)
          {
            setLimits(data);
          }
          else
          {
            setUnreachable(true);
          }
        }
      },
      () => current && setUnreachable(true),
    );

    return () =>
    {
      current = false;
    };
  }, [api]);

  return (
    <Container className="py-4">
      <h1 className="h3">Aemotional-Line</h1>
      <p>Pagina provvisoria: le schermate arrivano con i prossimi passi.</p>
      <p>
        {limits
          ? `Un messaggio può avere fino a ${limits.messageParagraphs} paragrafi.`
          : unreachable
            ? 'Il server non risponde, riprova tra qualche minuto.'
            : 'Caricamento…'}
      </p>
      <Dropdown>
        <Dropdown.Toggle variant="outline-secondary" id="provisional-menu">
          Menu di prova
        </Dropdown.Toggle>
        <Dropdown.Menu>
          <Dropdown.Item as="button" onClick={() => setOpen(true)}>
            Apri la finestra
          </Dropdown.Item>
        </Dropdown.Menu>
      </Dropdown>
      <Modal show={open} onHide={() => setOpen(false)}>
        <Modal.Header closeButton>
          <Modal.Title>Finestra di prova</Modal.Title>
        </Modal.Header>
        <Modal.Body>React-Bootstrap funziona con la politica di sicurezza dei contenuti.</Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setOpen(false)}>
            Chiudi
          </Button>
        </Modal.Footer>
      </Modal>
    </Container>
  );
}
