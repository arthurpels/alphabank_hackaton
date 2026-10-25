import { useState } from 'react';
import type { Author } from '../verdictLabels';
import './AuthorBadge.css';

export function AuthorBadge({ author }: { author: Author }) {
  const [open, setOpen] = useState(false);
  const hasDetails = author.details.length > 0;
  return (
    <div className="author">
      {hasDetails ? (
        <button
          type="button"
          className={`author__chip author__chip--${author.kind.toLowerCase()}`}
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
        >
          {author.title}
          <span className="author__more">{open ? 'скрыть' : 'почему?'}</span>
        </button>
      ) : (
        <span className={`author__chip author__chip--${author.kind.toLowerCase()}`}>{author.title}</span>
      )}
      {open && (
        <ul className="author__details">
          {author.details.map((d) => (
            <li key={d}>{d}</li>
          ))}
        </ul>
      )}
    </div>
  );
}