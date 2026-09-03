import React from 'react';
import { Link } from 'react-router-dom';
import { FileQuestion } from 'lucide-react';

export function NotFoundPage(): React.ReactElement {
  return (
    <div className="state-container" style={{ minHeight: '100vh' }}>
      <div className="state-icon mb-4 text-tertiary">
        <FileQuestion size={48} strokeWidth={1.5} />
      </div>
      <h1 className="state-title">Page Not Found</h1>
      <p className="state-message">
        The page you are looking for does not exist.
      </p>
      <Link
        to="/dashboard"
        id="link-home-from-404"
        style={{
          marginTop: 'var(--space-6)',
          color: 'var(--brand-light)',
          fontWeight: 500,
          fontSize: '0.875rem',
        }}
      >
        ← Return to Dashboard
      </Link>
    </div>
  );
}
