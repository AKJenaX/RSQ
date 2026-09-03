import React from 'react';
import {
  formatSeverity,
  getSeverityVariant,
  formatStatus,
  getStatusVariant,
} from '../utils/formatters';

// ── Severity Badge ────────────────────────────────────────────────────────────

interface SeverityBadgeProps {
  value: string | undefined;
}

export function SeverityBadge({ value }: SeverityBadgeProps): React.ReactElement {
  const variant = getSeverityVariant(value);
  const label = formatSeverity(value);

  return (
    <span
      className={`badge badge-severity-${variant}`}
      title={`Severity: ${label}`}
      aria-label={`Severity: ${label}`}
    >
      <span className="badge-dot" aria-hidden="true" />
      {label}
    </span>
  );
}

// ── Status Badge ──────────────────────────────────────────────────────────────

interface StatusBadgeProps {
  value: string | undefined;
}

export function StatusBadge({ value }: StatusBadgeProps): React.ReactElement {
  const variant = getStatusVariant(value);
  const label = formatStatus(value);

  return (
    <span
      className={`badge badge-status-${variant}`}
      title={`Status: ${label}`}
      aria-label={`Status: ${label}`}
    >
      <span className="badge-dot" aria-hidden="true" />
      {label}
    </span>
  );
}
