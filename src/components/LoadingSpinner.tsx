import React from 'react';
import { Loader2 } from 'lucide-react';

interface LoadingSpinnerProps {
  message?: string;
}

export function LoadingSpinner({ message = 'Loading…' }: LoadingSpinnerProps): React.ReactElement {
  return (
    <div className="state-container">
      <Loader2 className="state-icon spinner" size={32} aria-hidden="true" />
      <div className="state-title">{message}</div>
    </div>
  );
}
