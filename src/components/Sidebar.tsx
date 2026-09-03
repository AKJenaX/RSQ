import React from 'react';
import { NavLink } from 'react-router-dom';
import { signOut } from 'firebase/auth';
import { Activity, LayoutDashboard, FileText, LogOut, Users, Box, History } from 'lucide-react';
import { auth } from '../firebase/config';
import { useAuth } from '../hooks/useAuth';

export function Sidebar(): React.ReactElement {
  const { user } = useAuth();
  
  const handleSignOut = async () => {
    try {
      await signOut(auth);
    } catch (err) {
      console.error('[Sidebar] Sign out error:', err);
    }
  };

  const displayName = user?.displayName ?? user?.email ?? 'Authority User';

  return (
    <aside className="sidebar" role="navigation" aria-label="Main navigation">
      {/* Logo */}
      <div className="sidebar-logo">
        <Activity className="sidebar-logo-icon" />
        <div className="sidebar-logo-text">
          <span className="sidebar-logo-title">RSQ</span>
          <span className="sidebar-logo-subtitle">Authority</span>
        </div>
      </div>

      {/* Navigation */}
      <nav className="sidebar-nav">
        {/* OVERVIEW */}
        <div className="sidebar-nav-label">Overview</div>
        <NavLink
          id="nav-dashboard"
          to="/dashboard"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <LayoutDashboard className="sidebar-nav-icon" size={16} />
          Dashboard
        </NavLink>

        {/* OPERATIONS */}
        <div className="sidebar-nav-label mt-4">Operations</div>
        <NavLink
          id="nav-reports"
          to="/reports"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <FileText className="sidebar-nav-icon" size={16} />
          Incidents
        </NavLink>
        <NavLink
          id="nav-volunteers"
          to="/volunteers"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <Users className="sidebar-nav-icon" size={16} />
          Volunteers
        </NavLink>
        <NavLink
          id="nav-resources"
          to="/resources"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <Box className="sidebar-nav-icon" size={16} />
          Resources
        </NavLink>

        {/* MONITORING */}
        <div className="sidebar-nav-label mt-4">Monitoring</div>
        <NavLink
          id="nav-activity"
          to="/activity"
          className={({ isActive }) =>
            `sidebar-nav-item${isActive ? ' active' : ''}`
          }
        >
          <History className="sidebar-nav-icon" size={16} />
          Activity
        </NavLink>
      </nav>

      {/* Footer */}
      <div className="sidebar-footer">
        <div className="user-profile">
          <div className="user-info-stack">
            <span className="user-email">{displayName}</span>
            <span className="user-role">Authority</span>
          </div>
        </div>
        <button
          id="btn-sidebar-signout"
          className="btn-signout"
          onClick={handleSignOut}
          aria-label="Sign out of RSQ Authority Dashboard"
        >
          <LogOut size={14} />
          Sign Out
        </button>
      </div>
    </aside>
  );
}
