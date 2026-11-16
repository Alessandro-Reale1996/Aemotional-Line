import type { ChangeEvent } from 'react';
import Form from 'react-bootstrap/Form';

interface Props
{
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  /** The limit of GET /api/limits, in UTF-16 units like the server and JavaScript's length. */
  maxLength: number;
  /** A multi-line field with that many rows; a single line without it. */
  rows?: number;
  hint?: string;
  disabled?: boolean;
}

/**
 * A field of user's text (CLAUDE.md, frontend points 4, 6 and 7): its limit can't be exceeded and a counter shows how much
 * is used; the browser doesn't remember what was typed (autocomplete off), since the device may be shared.
 */
export function TextField({ id, label, value, onChange, maxLength, rows, hint, disabled }: Props)
{
  const common = {
    value,
    onChange: (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => onChange(event.target.value),
    maxLength,
    autoComplete: 'off',
    disabled,
    'aria-describedby': `${id}-counter`,
  };

  return (
    <Form.Group className="mb-3" controlId={id}>
      <Form.Label>{label}</Form.Label>
      {rows ? <Form.Control as="textarea" rows={rows} {...common} /> : <Form.Control {...common} />}
      <div className="d-flex justify-content-between gap-2">
        <Form.Text muted>{hint}</Form.Text>
        <Form.Text id={`${id}-counter`} muted aria-label={`${value.length} caratteri su ${maxLength}`}>
          {value.length}/{maxLength}
        </Form.Text>
      </div>
    </Form.Group>
  );
}

/** A user's text as written: plain text with its line breaks (frontend point 8). */
export function UserText({ text, className = '' }: { text: string; className?: string })
{
  return <p className={`preserve-lines ${className}`.trim()}>{text}</p>;
}
