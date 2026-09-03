import React from 'react';
import { FileSearch } from 'lucide-react';

interface EmptyStateProps {
  icon?: string;
  title: string;
  message: string;
  type?: 'default' | 'warning';
}

export function EmptyState({ title, message, type = 'default' }: EmptyStateProps): React.ReactElement {
  return (
    <div className={`empty-state ${type === 'warning' ? 'warning-state' : ''}`}>
      <FileSearch className="empty-state-icon" size={48} aria-hidden="true" />
      <h3 className="empty-state-title">{title}</h3>
      <p className="empty-state-message">{message}</p>
    </div>
  );
}
