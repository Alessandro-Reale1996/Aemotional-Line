import Button from 'react-bootstrap/Button';
import Modal from 'react-bootstrap/Modal';

interface Props
{
  show: boolean;
  text: string;
  confirm: string;
  cancel: string;
  onConfirm: () => void;
  onCancel: () => void;
}

/** Asks before something that loses what the user wrote (frontend point 7). Cancelling is the default. */
export function ConfirmDialog({ show, text, confirm, cancel, onConfirm, onCancel }: Props)
{
  return (
    <Modal show={show} onHide={onCancel} centered aria-labelledby="confirm-title">
      <Modal.Header closeButton closeLabel="Chiudi">
        <Modal.Title id="confirm-title" className="h5">
          Testo non inviato
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>{text}</Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={onCancel} autoFocus>
          {cancel}
        </Button>
        <Button variant="danger" onClick={onConfirm}>
          {confirm}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
