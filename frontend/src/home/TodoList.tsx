import { Link } from 'react-router';

import type { TodoItem } from './todo';

/** What waits for the user: each item a link when its page exists (F5 decision 1). */
export function TodoList({ items, className = '' }: { items: TodoItem[]; className?: string })
{
  return (
    <ul className={className}>
      {items.map((item, index) => (
        <li key={index}>{item.to ? <Link to={item.to}>{item.text}</Link> : item.text}</li>
      ))}
    </ul>
  );
}
