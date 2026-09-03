import React from 'react';

interface StatCardProps {
  label: string;
  value: string | number;
  indicatorClass?: string;
  description?: string;
}

export function StatCard({ label, value, indicatorClass, description }: StatCardProps): React.ReactElement {
  return (
    <div className="stat-card">
      <div className="stat-card-label">{label}</div>
      <div className="stat-card-value-row">
        <div className="stat-card-value">{value}</div>
        {indicatorClass && (
          <div className={`stat-indicator ${indicatorClass}`} aria-hidden="true" />
        )}
      </div>
      {description && <div className="stat-card-desc">{description}</div>}
    </div>
  );
}
