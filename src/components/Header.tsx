import React from 'react';
import type { User } from 'firebase/auth';

interface HeaderProps {
  user: User | null;
  title: string;
  subtitle?: string;
}

export function Header({ title, subtitle }: HeaderProps): React.ReactElement {
  return (
    <header className="header" role="banner">
      <div>
        <div className="header-title">{title}</div>
        {subtitle && <div className="header-subtitle">{subtitle}</div>}
      </div>

      <div className="header-right">
        {/* Live indicator */}
        <div className="live-indicator" title="Real-time updates active">
          <div className="live-dot" aria-hidden="true" />
          Live
        </div>
      </div>
    </header>
  );
}
