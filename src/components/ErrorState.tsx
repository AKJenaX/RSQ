import React from 'react';
import { AlertCircle, AlertTriangle, HelpCircle } from 'lucide-react';
import type { ReportsLoadState } from '../hooks/useReports';

interface ErrorStateProps {
  type: ReportsLoadState | 'not-found' | 'error';
  message?: string;
}

export function ErrorState({ type, message }: ErrorStateProps): React.ReactElement {
  let icon = <AlertTriangle className="state-icon text-error" size={48} aria-hidden="true" />;
  let title = 'Unable to load data';
  let desc = message ?? 'An unexpected error occurred. Please try again.';

  if (type === 'not-configured') {
    icon = <AlertCircle className="state-icon text-warning" size={48} aria-hidden="true" />;
    title = 'Firebase Not Configured';
    desc = 'Please provide Firebase configuration to connect to the database.';
  } else if (type === 'permission-denied') {
    icon = <AlertCircle className="state-icon text-error" size={48} aria-hidden="true" />;
    title = 'Access Denied';
    desc = message ?? 'You do not have permission to view this content. Please check Firestore security rules.';
  } else if (type === 'not-found') {
    icon = <HelpCircle className="state-icon text-muted" size={48} aria-hidden="true" />;
    title = 'Report Not Found';
    desc = 'The requested report could not be found or has been removed.';
  }

  return (
    <div className="state-container" role="alert">
      {icon}
      <h3 className="state-title">{title}</h3>
      <p className="state-message">{desc}</p>
    </div>
  );
}
