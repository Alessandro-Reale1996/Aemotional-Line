import { useState } from 'react';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import InputGroup from 'react-bootstrap/InputGroup';

interface Props
{
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  /** current-password at the login, new-password where one is chosen: password managers rely on it (frontend point 7). */
  autoComplete: 'current-password' | 'new-password';
  /** The rule, always shown under a new password (F4 decision 5). */
  rule?: string;
  disabled?: boolean;
}

/** A password field with a "Mostra" button instead of a field to repeat it (F4 decision 5). */
export function PasswordField({ id, label, value, onChange, autoComplete, rule, disabled }: Props)
{
  const [shown, setShown] = useState(false);

  return (
    <Form.Group className="mb-3" controlId={id}>
      <Form.Label>{label}</Form.Label>
      <InputGroup>
        <Form.Control
          type={shown ? 'text' : 'password'}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          autoComplete={autoComplete}
          autoCapitalize="off"
          spellCheck={false}
          disabled={disabled}
          aria-describedby={rule ? `${id}-rule` : undefined}
        />
        <Button
          variant="outline-secondary"
          onClick={() => setShown((was) => !was)}
          aria-label={shown ? 'Nascondi la password' : 'Mostra la password'}
          aria-pressed={shown}
          disabled={disabled}
        >
          {shown ? 'Nascondi' : 'Mostra'}
        </Button>
      </InputGroup>
      {rule && (
        <Form.Text id={`${id}-rule`} muted>
          {rule}
        </Form.Text>
      )}
    </Form.Group>
  );
}
